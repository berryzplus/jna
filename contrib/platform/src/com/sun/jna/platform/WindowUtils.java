/*
 * Copyright (c) 2007-2008 Timothy Wall, All Rights Reserved
 * Parts Copyright (c) 2007 Olivier Chafik
 *
 * This library is free software; you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation;
 * either version 2.1 of the License, or (at your option) any later
 * version. <p/> This library is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */
package com.sun.jna.platform;

import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ContainerEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.PopupFactory;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.unix.X11;
import com.sun.jna.platform.unix.X11.Display;
import com.sun.jna.platform.unix.X11.GC;
import com.sun.jna.platform.unix.X11.Pixmap;
import com.sun.jna.platform.unix.X11.XVisualInfo;
import com.sun.jna.platform.unix.X11.Xext;
import com.sun.jna.platform.unix.X11.Xrender.XRenderPictFormat;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HBITMAP;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinDef.HRGN;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.BLENDFUNCTION;
import com.sun.jna.platform.win32.WinUser.POINT;
import com.sun.jna.platform.win32.WinUser.SIZE;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Provides additional features on a Java {@link Window}.
 * <ul>
 * <li>Non-rectangular shape (bitmap mask, no antialiasing)
 * <li>Transparency (constant alpha applied to window contents or
 * transparent background)
 * <li>Fully transparent window (the transparency of all painted pixels is
 * applied to the window).
 * </ul>
 * NOTE: since there is no explicit way to force PopupFactory to use a
 * heavyweight popup, and anything but a heavyweight popup will be
 * clipped by a window mask, an additional subwindow is added to all
 * masked windows to implicitly force PopupFactory to use a heavyweight
 * window and avoid clipping.
 * <p>
 * NOTE: Neither shaped windows nor transparency
 * currently works with Java 1.4 under X11. This is at least partly due
 * to 1.4 using multiple X11 windows for a single given Java window. It
 * *might* be possible to remedy by applying the window
 * region/transparency to all descendants, but I haven't tried it. In
 * addition, windows must be both displayable <em>and</em> visible
 * before the corresponding native Drawable may be obtained; in later
 * Java versions, the window need only be displayable.
 * <p>
 * NOTE: If you use {@link #setWindowMask(Window,Shape)} and override {@link
 * Window#paint(Graphics)} on OS X, you'll need to explicitly set the clip
 * mask on the <code>Graphics</code> object with the window mask; only the
 * content pane of the window and below have the window mask automatically
 * applied.<p>
 * NOTE: On OSX, the property
 * <code>apple.awt.draggableWindowBackground</code> is set automatically when
 * a window's background color has an alpha component.  That property must be
 * set to its final value <em>before</em> the heavyweight peer for the Window
 * is created.  Once {@link Component#addNotify} has been called on the
 * component, causing creation of the heavyweight peer, changing this
 * property has no effect.
 * @see <a href="http://developer.apple.com/technotes/tn2007/tn2196.html#APPLE_AWT_DRAGGABLEWINDOWBACKGROUND">Apple Technote 2007</a>
 */
// TODO: setWindowMask() should accept a threshold; some cases want a
// 50% threshold, some might want zero/non-zero
public class WindowUtils {

    private static final String TRANSPARENT_OLD_BG = "transparent-old-bg";
    private static final String TRANSPARENT_OLD_OPAQUE = "transparent-old-opaque";
    private static final String TRANSPARENT_ALPHA = "transparent-alpha";

    /** Use this to clear a window mask. */
    public static final Shape MASK_NONE = null;

    /**
     * This class forces a heavyweight popup on the parent
     * {@link Window}. See the implementation of {@link PopupFactory};
     * a heavyweight is forced if there is an occluding subwindow on the
     * target window.
     * <p>
     * Ideally we'd have more control over {@link PopupFactory} but this
     * is a fairly simple, lightweight workaround.  Note that, at least as of
     * JDK 1.6, the following do not have the desired effect:<br>
     * <code><pre>
     * ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
     * JPopupMenu.setDefaultLightWeightPopupEnabled(false);
     * System.setProperty("JPopupMenu.defaultLWPopupEnabledKey", "false");
     * </pre></code>
     */
    private static class HeavyweightForcer extends Window {
		private static final long serialVersionUID = 1L;
        private final boolean packed;

        public HeavyweightForcer(final Window parent) {
            super(parent);
            pack();
            packed = true;
        }

        public boolean isVisible() {
            // Only want to be 'visible' once the peer is instantiated
            // via pack; this tricks PopupFactory into using a heavyweight
            // popup to avoid being obscured by this window
            return packed;
        }

        public Rectangle getBounds() {
            return getOwner().getBounds();
        }
    }
    /**
     * This can be installed over a {@link JLayeredPane} in order to
     * listen for repaint requests. The content's repaint method will be
     * invoked whenever any part of the ancestor window is repainted.
     */
    protected static class RepaintTrigger extends JComponent {
		private static final long serialVersionUID = 1L;

        protected class Listener
            extends WindowAdapter
            implements ComponentListener, HierarchyListener, AWTEventListener {
            public void windowOpened(final WindowEvent e) {
                repaint();
            }

            public void componentHidden(final ComponentEvent e) {}

            public void componentMoved(final ComponentEvent e) {}

            public void componentResized(final ComponentEvent e) {
                setSize(getParent().getSize());
                repaint();
            }

            public void componentShown(final ComponentEvent e) {
                repaint();
            }

            public void hierarchyChanged(final HierarchyEvent e) {
                repaint();
            }

            public void eventDispatched(final AWTEvent e) {
                if (e instanceof MouseEvent) {
                    final Component src = ((MouseEvent)e).getComponent();
                    if (src != null
                        && SwingUtilities.isDescendingFrom(src, content)) {
                        final MouseEvent me = SwingUtilities.convertMouseEvent(src, (MouseEvent)e, content);
                        final Component c = SwingUtilities.getDeepestComponentAt(content, me.getX(), me.getY());
                        if (c != null) {
                            setCursor(c.getCursor());
                        }
                    }
                }
            }
        }

        private final Listener listener = createListener();
        private final JComponent content;

        public RepaintTrigger(final JComponent content) {
            this.content = content;
        }

        public void addNotify() {
            super.addNotify();
            final Window w = SwingUtilities.getWindowAncestor(this);
            setSize(getParent().getSize());
            w.addComponentListener(listener);
            w.addWindowListener(listener);
            Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK|AWTEvent.MOUSE_MOTION_EVENT_MASK);
        }

        public void removeNotify() {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
            final Window w = SwingUtilities.getWindowAncestor(this);
            w.removeComponentListener(listener);
            w.removeWindowListener(listener);
            super.removeNotify();
        }

        private Rectangle dirty;
        protected void paintComponent(final Graphics g) {
            final Rectangle bounds = g.getClipBounds();
            if (dirty == null || !dirty.contains(bounds)) {
                if (dirty == null) {
                    dirty = bounds;
                }
                else {
                    dirty = dirty.union(bounds);
                }
                content.repaint(dirty);
            }
            else {
                dirty = null;
            }
        }

        protected Listener createListener() {
            return new Listener();
        }
    };

    /** Window utilities with differing native implementations. */
    public static abstract class NativeWindowUtils {
        protected abstract class TransparentContentPane
            extends JPanel implements AWTEventListener {
    		private static final long serialVersionUID = 1L;
            private boolean transparent;
            public TransparentContentPane(final Container oldContent) {
                super(new BorderLayout());
                add(oldContent, BorderLayout.CENTER);
                setTransparent(true);
                if (oldContent instanceof JPanel) {
                    ((JComponent)oldContent).setOpaque(false);
                }
            }
            public void addNotify() {
                super.addNotify();
                Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.CONTAINER_EVENT_MASK);
            }
            public void removeNotify() {
                Toolkit.getDefaultToolkit().removeAWTEventListener(this);
                super.removeNotify();
            }
            public void setTransparent(final boolean transparent) {
                this.transparent = transparent;
                setOpaque(!transparent);
                setDoubleBuffered(!transparent);
                repaint();
            }
            public void eventDispatched(final AWTEvent e) {
                if (e.getID() == ContainerEvent.COMPONENT_ADDED
                    && SwingUtilities.isDescendingFrom(((ContainerEvent)e).getChild(), this)) {
                    final Component child = ((ContainerEvent)e).getChild();
                    NativeWindowUtils.this.setDoubleBuffered(child, false);
                }
            }
            public void paint(final Graphics gr) {
                if (transparent) {
                    final Rectangle r = gr.getClipBounds();
                    final int w = r.width;
                    final int h = r.height;
                    if (getWidth() > 0 && getHeight() > 0) {
                        final BufferedImage buf =
                            new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);

                        Graphics2D g = buf.createGraphics();
                        g.setComposite(AlphaComposite.Clear);
                        g.fillRect(0, 0, w, h);
                        g.dispose();

                        g = buf.createGraphics();
                        g.translate(-r.x, -r.y);
                        super.paint(g);
                        g.dispose();

                        paintDirect(buf, r);
                    }
                }
                else {
                    super.paint(gr);
                }
            }
            /** Use the contents of the given BufferedImage to paint directly
             * on this component's ancestor window.
             */
            protected abstract void paintDirect(BufferedImage buf, Rectangle bounds);
        }

        protected Window getWindow(final Component c) {
            return c instanceof Window
                ? (Window)c : SwingUtilities.getWindowAncestor(c);
        }
        /**
         * Execute the given action when the given window becomes
         * displayable.
         */
        protected void whenDisplayable(final Component w, final Runnable action) {
            if (w.isDisplayable() && (!Holder.requiresVisible || w.isVisible())) {
                action.run();
            }
            else if (Holder.requiresVisible) {
                getWindow(w).addWindowListener(new WindowAdapter() {
                    public void windowOpened(final WindowEvent e) {
                        e.getWindow().removeWindowListener(this);
                        action.run();
                    }
                    public void windowClosed(final WindowEvent e) {
                        e.getWindow().removeWindowListener(this);
                    }
                });
            }
            else {
                // Hierarchy events are fired in direct response to
                // displayability changes
                w.addHierarchyListener(new HierarchyListener() {
                    public void hierarchyChanged(final HierarchyEvent e) {
                        if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                            && e.getComponent().isDisplayable()) {
                            e.getComponent().removeHierarchyListener(this);
                            action.run();
                        }
                    }
                });
            }
        }

        protected Raster toRaster(final Shape mask) {
            Raster raster = null;
            if (mask != MASK_NONE) {
                final Rectangle bounds = mask.getBounds();
                if (bounds.width > 0 && bounds.height > 0) {
                    final BufferedImage clip =
                        new BufferedImage(bounds.x + bounds.width,
                                          bounds.y + bounds.height,
                                          BufferedImage.TYPE_BYTE_BINARY);
                    final Graphics2D g = clip.createGraphics();
                    g.setColor(Color.black);
                    g.fillRect(0, 0, bounds.x + bounds.width, bounds.y + bounds.height);
                    g.setColor(Color.white);
                    g.fill(mask);
                    raster = clip.getRaster();
                }
            }
            return raster;
        }

        protected Raster toRaster(final Component c, final Icon mask) {
            Raster raster = null;
            if (mask != null) {
                final Rectangle bounds = new Rectangle(0, 0, mask.getIconWidth(),
                                                 mask.getIconHeight());
                final BufferedImage clip = new BufferedImage(bounds.width,
                                                       bounds.height,
                                                       BufferedImage.TYPE_INT_ARGB);
                final Graphics2D g = clip.createGraphics();
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, bounds.width, bounds.height);
                g.setComposite(AlphaComposite.SrcOver);
                mask.paintIcon(c, g, 0, 0);
                raster = clip.getAlphaRaster();
            }
            return raster;
        }

        protected Shape toShape(final Raster raster) {
            final Area area = new Area(new Rectangle(0, 0, 0, 0));
            RasterRangesUtils.outputOccupiedRanges(raster, new RasterRangesUtils.RangesOutput() {
                public boolean outputRange(final int x, final int y, final int w, final int h) {
                    area.add(new Area(new Rectangle(x, y, w, h)));
                    return true;
                }
            });
            return area;
        }

        /**
         * Set the overall alpha transparency of the window. An alpha of
         * 1.0 is fully opaque, 0.0 is fully transparent.
         */
        public void setWindowAlpha(final Window w, final float alpha) {
            // do nothing
        }

        /** Default: no support. */
        public boolean isWindowAlphaSupported() {
            return false;
        }

        /** Return the default graphics configuration. */
        public GraphicsConfiguration getAlphaCompatibleGraphicsConfiguration() {
            final GraphicsEnvironment env = GraphicsEnvironment
                                                         .getLocalGraphicsEnvironment();
            final GraphicsDevice dev = env.getDefaultScreenDevice();
            return dev.getDefaultConfiguration();
        }

        /**
         * Set the window to be transparent. Only explicitly painted
         * pixels will be non-transparent. All pixels will be composited
         * with whatever is under the window using their alpha values.
         */
        public void setWindowTransparent(final Window w, final boolean transparent) {
            // do nothing
        }

        protected void setDoubleBuffered(final Component root, final boolean buffered) {
            if (root instanceof JComponent) {
                ((JComponent)root).setDoubleBuffered(buffered);
            }
            if (root instanceof JRootPane && buffered) {
                ((JRootPane)root).setDoubleBuffered(true);
            }
            else if (root instanceof Container) {
                final Component[] kids = ((Container)root).getComponents();
                for (int i=0;i < kids.length;i++) {
                    setDoubleBuffered(kids[i], buffered);
                }
            }
        }

        protected void setLayersTransparent(final Window w, final boolean transparent) {

            Color bg = transparent ? new Color(0, 0, 0, 0) : null;
            if (w instanceof RootPaneContainer) {
                final RootPaneContainer rpc = (RootPaneContainer)w;
                final JRootPane root = rpc.getRootPane();
                final JLayeredPane lp = root.getLayeredPane();
                final Container c = root.getContentPane();
                final JComponent content =
                    c instanceof JComponent ? (JComponent)c : null;
                if (transparent) {
                    lp.putClientProperty(TRANSPARENT_OLD_OPAQUE,
                                         Boolean.valueOf(lp.isOpaque()));
                    lp.setOpaque(false);
                    root.putClientProperty(TRANSPARENT_OLD_OPAQUE,
                                           Boolean.valueOf(root.isOpaque()));
                    root.setOpaque(false);
                    if (content != null) {
                        content.putClientProperty(TRANSPARENT_OLD_OPAQUE,
                                                  Boolean.valueOf(content.isOpaque()));
                        content.setOpaque(false);
                    }
                    root.putClientProperty(TRANSPARENT_OLD_BG,
                                           root.getParent().getBackground());
                }
                else {
                    lp.setOpaque(Boolean.TRUE.equals(lp.getClientProperty(TRANSPARENT_OLD_OPAQUE)));
                    lp.putClientProperty(TRANSPARENT_OLD_OPAQUE, null);
                    root.setOpaque(Boolean.TRUE.equals(root.getClientProperty(TRANSPARENT_OLD_OPAQUE)));
                    root.putClientProperty(TRANSPARENT_OLD_OPAQUE, null);
                    if (content != null) {
                        content.setOpaque(Boolean.TRUE.equals(content.getClientProperty(TRANSPARENT_OLD_OPAQUE)));
                        content.putClientProperty(TRANSPARENT_OLD_OPAQUE, null);
                    }
                    bg = (Color)root.getClientProperty(TRANSPARENT_OLD_BG);
                    root.putClientProperty(TRANSPARENT_OLD_BG, null);
                }
            }
            w.setBackground(bg);
        }

        /** Override this method to provide bitmap masking of the given
         * heavyweight component.
         */
        protected void setMask(final Component c, final Raster raster) {
            throw new UnsupportedOperationException("Window masking is not available");
        }

        /**
         * Set the window mask based on the given Raster, which should
         * be treated as a bitmap (zero/nonzero values only). A value of
         * <code>null</code> means to remove the mask.
         */
        protected void setWindowMask(final Component w, final Raster raster) {
            if (w.isLightweight())
                throw new IllegalArgumentException("Component must be heavyweight: " + w);
            setMask(w, raster);
        }

        /** Set the window mask based on a {@link Shape}. */
        public void setWindowMask(final Component w, final Shape mask) {
            setWindowMask(w, toRaster(mask));
        }

        /**
         * Set the window mask based on an Icon. All non-transparent
         * pixels will be included in the mask.
         */
        public void setWindowMask(final Component w, final Icon mask) {
            setWindowMask(w, toRaster(w, mask));
        }

        /**
         * Use this method to ensure heavyweight popups are used in
         * conjunction with a given window. This prevents the window's
         * alpha setting or mask region from being applied to the popup.
         */
        protected void setForceHeavyweightPopups(final Window w, final boolean force) {
            if (!(w instanceof HeavyweightForcer)) {
                final Window[] owned = w.getOwnedWindows();
                for (int i = 0; i < owned.length; i++) {
                    if (owned[i] instanceof HeavyweightForcer) {
                        if (force)
                            return;
                        owned[i].dispose();
                    }
                }
                final Boolean b = Boolean.valueOf(System.getProperty("jna.force_hw_popups", "true"));
                if (force && b.booleanValue()) {
                    new HeavyweightForcer(w);
                }
            }
        }
    }
    /** Canonical lazy loading of a singleton. */
    private static class Holder {
        /**
         * Indicates whether a window must be visible before its native
         * handle can be obtained. This wart is caused by the Java
         * 1.4/X11 implementation.
         */
        public static boolean requiresVisible;
        public static final NativeWindowUtils INSTANCE;
        static {
            if (Platform.isWindows()) {
                INSTANCE = new W32WindowUtils();
            }
            else if (Platform.isMac()) {
                INSTANCE = new MacWindowUtils();
            }
            else if (Platform.isX11()) {
                INSTANCE = new X11WindowUtils();
                requiresVisible = System.getProperty("java.version")
                                        .matches("^1\\.4\\..*");
            }
            else {
                final String os = System.getProperty("os.name");
                throw new UnsupportedOperationException("No support for " + os);
            }
        }
    }

    private static NativeWindowUtils getInstance() {
        return Holder.INSTANCE;
    }

    private static class W32WindowUtils extends NativeWindowUtils {
        private HWND getHWnd(final Component w) {
            final HWND hwnd = new HWND();
            hwnd.setPointer(Native.getComponentPointer(w));
            return hwnd;
        }

        /**
         * W32 alpha will only work if <code>sun.java2d.noddraw</code>
         * is set
         */
        public boolean isWindowAlphaSupported() {
            return Boolean.getBoolean("sun.java2d.noddraw");
        }

        /** Indicates whether UpdateLayeredWindow is in use. */
        private boolean usingUpdateLayeredWindow(final Window w) {
            if (w instanceof RootPaneContainer) {
                final JRootPane root = ((RootPaneContainer)w).getRootPane();
                return root.getClientProperty(TRANSPARENT_OLD_BG) != null;
            }
            return false;
        }

        /** Keep track of the alpha level, since we can't read it from
         * the window itself.
         */
        private void storeAlpha(final Window w, final byte alpha) {
            if (w instanceof RootPaneContainer) {
                final JRootPane root = ((RootPaneContainer)w).getRootPane();
                final Byte b = alpha == (byte)0xFF ? null : Byte.valueOf(alpha);
                root.putClientProperty(TRANSPARENT_ALPHA, b);
            }
        }

        /** Return the last alpha level we set on the window. */
        private byte getAlpha(final Window w) {
            if (w instanceof RootPaneContainer) {
                final JRootPane root = ((RootPaneContainer)w).getRootPane();
                final Byte b = (Byte)root.getClientProperty(TRANSPARENT_ALPHA);
                if (b != null)
                    return b.byteValue();
            }
            return (byte)0xFF;
        }

        public void setWindowAlpha(final Window w, final float alpha) {
            if (!isWindowAlphaSupported())
                throw new UnsupportedOperationException("Set sun.java2d.noddraw=true to enable transparent windows");
            whenDisplayable(w, new Runnable() {
                public void run() {
                    final HWND hWnd = getHWnd(w);
                    final User32 user = User32.INSTANCE;
                    int flags = user.GetWindowLong(hWnd, WinUser.GWL_EXSTYLE);
                    final byte level = (byte)((int)(255 * alpha) & 0xFF);
                    if (usingUpdateLayeredWindow(w)) {
                        // If already using UpdateLayeredWindow, continue to
                        // do so
                        final BLENDFUNCTION blend = new BLENDFUNCTION();
                        blend.SourceConstantAlpha = level;
                        blend.AlphaFormat = WinUser.AC_SRC_ALPHA;
                        user.UpdateLayeredWindow(hWnd, null, null, null, null,
                                                 null, 0, blend,
                                                 WinUser.ULW_ALPHA);
                    }
                    else if (alpha == 1f) {
                        flags &= ~WinUser.WS_EX_LAYERED;
                        user.SetWindowLong(hWnd, WinUser.GWL_EXSTYLE, flags);
                    }
                    else {
                        flags |= WinUser.WS_EX_LAYERED;
                        user.SetWindowLong(hWnd, WinUser.GWL_EXSTYLE, flags);
                        user.SetLayeredWindowAttributes(hWnd, 0, level,
                        		WinUser.LWA_ALPHA);
                    }
                    setForceHeavyweightPopups(w, alpha != 1f);
                    storeAlpha(w, level);
                }
            });
        }

        /** W32 makes the client responsible for repainting the <em>entire</em>
         * window on any change.  It also does not paint window decorations
         * when the window is transparent.
         */
        private class W32TransparentContentPane extends TransparentContentPane {
    		private static final long serialVersionUID = 1L;
            private HDC memDC;
            private HBITMAP hBitmap;
            private Pointer pbits;
            private Dimension bitmapSize;
            public W32TransparentContentPane(final Container content) {
                super(content);
            }
            private void disposeBackingStore() {
                final GDI32 gdi = GDI32.INSTANCE;
                if (hBitmap != null) {
                    gdi.DeleteObject(hBitmap);
                    hBitmap = null;
                }
                if (memDC != null) {
                    gdi.DeleteDC(memDC);
                    memDC = null;
                }
            }
            public void removeNotify() {
                super.removeNotify();
                disposeBackingStore();
            }
            public void setTransparent(final boolean transparent) {
                super.setTransparent(transparent);
                if (!transparent) {
                    disposeBackingStore();
                }
            }
            protected void paintDirect(final BufferedImage buf, final Rectangle bounds) {
                // TODO: paint frame decoration if window is decorated
                final Window win = SwingUtilities.getWindowAncestor(this);
                final GDI32 gdi = GDI32.INSTANCE;
                final User32 user = User32.INSTANCE;
                final int x = bounds.x;
                final int y = bounds.y;
                final Point origin = SwingUtilities.convertPoint(this, x, y, win);
                final int w = bounds.width;
                final int h = bounds.height;
                final int ww = win.getWidth();
                final int wh = win.getHeight();
                final HDC screenDC = user.GetDC(null);
                HANDLE oldBitmap = null;
                try {
                    if (memDC == null) {
                        memDC = gdi.CreateCompatibleDC(screenDC);
                    }
                    if (hBitmap == null || !win.getSize().equals(bitmapSize)) {
                        if (hBitmap != null) {
                            gdi.DeleteObject(hBitmap);
                            hBitmap = null;
                        }
                        final BITMAPINFO bmi = new BITMAPINFO();
                        bmi.bmiHeader.biWidth = ww;
                        bmi.bmiHeader.biHeight = wh;
                        bmi.bmiHeader.biPlanes = 1;
                        bmi.bmiHeader.biBitCount = 32;
                        bmi.bmiHeader.biCompression = WinGDI.BI_RGB;
                        bmi.bmiHeader.biSizeImage = ww * wh * 4;
                        final PointerByReference ppbits = new PointerByReference();
                        hBitmap = gdi.CreateDIBSection(memDC, bmi,
                        		WinGDI.DIB_RGB_COLORS,
                        		ppbits, null, 0);
                        pbits = ppbits.getValue();
                        bitmapSize = new Dimension(ww, wh);
                    }
                    oldBitmap = gdi.SelectObject(memDC, hBitmap);
                    final Raster raster = buf.getData();
                    final int[] pixel = new int[4];
                    final int[] bits = new int[w];
                    for (int row = 0; row < h; row++) {
                        for (int col = 0; col < w; col++) {
                            raster.getPixel(col, row, pixel);
                            final int alpha = (pixel[3] & 0xFF) << 24;
                            final int red = pixel[2] & 0xFF;
                            final int green = (pixel[1] & 0xFF) << 8;
                            final int blue = (pixel[0] & 0xFF) << 16;
                            bits[col] = alpha | red | green | blue;
                        }
                        final int v = wh - (origin.y + row) - 1;
                        pbits.write((v*ww+origin.x)*4, bits, 0, bits.length);
                    }
                    final SIZE winSize = new SIZE();
                    winSize.cx = win.getWidth();
                    winSize.cy = win.getHeight();
                    final POINT winLoc = new POINT();
                    winLoc.x = win.getX();
                    winLoc.y = win.getY();
                    final POINT srcLoc = new POINT();
                    final BLENDFUNCTION blend = new BLENDFUNCTION();
                    final HWND hWnd = getHWnd(win);
                    // extract current constant alpha setting, if possible
                    final ByteByReference bref = new ByteByReference();
                    final IntByReference iref = new IntByReference();
                    byte level = getAlpha(win);
                    try {
                        // GetLayeredwindowAttributes supported WinXP and later
                        if (user.GetLayeredWindowAttributes(hWnd, null, bref, iref)
                            && (iref.getValue() & WinUser.LWA_ALPHA) != 0) {
                            level = bref.getValue();
                        }
                    }
                    catch(final UnsatisfiedLinkError e) {
                    }
                    blend.SourceConstantAlpha = level;
                    blend.AlphaFormat = WinUser.AC_SRC_ALPHA;
                    user.UpdateLayeredWindow(hWnd, screenDC, winLoc, winSize, memDC,
                                             srcLoc, 0, blend, WinUser.ULW_ALPHA);
                } finally {
                    user.ReleaseDC(null, screenDC);
                    if (memDC != null && oldBitmap != null) {
                        gdi.SelectObject(memDC, oldBitmap);
                    }
                }
            }
        }

        /** Note that w32 does <em>not</em> paint window decorations when
         * the window is transparent.
         */
        public void setWindowTransparent(final Window w,
                                         final boolean transparent) {
            if (!(w instanceof RootPaneContainer))
                throw new IllegalArgumentException("Window must be a RootPaneContainer");
            if (!isWindowAlphaSupported())
                throw new UnsupportedOperationException("Set sun.java2d.noddraw=true to enable transparent windows");
            final boolean isTransparent = w.getBackground() != null
                && w.getBackground().getAlpha() == 0;
            if (transparent == isTransparent)
                return;
            whenDisplayable(w, new Runnable() {
                public void run() {
                    final User32 user = User32.INSTANCE;
                    final HWND hWnd = getHWnd(w);
                    int flags = user.GetWindowLong(hWnd, WinUser.GWL_EXSTYLE);
                    final JRootPane root = ((RootPaneContainer)w).getRootPane();
                    final JLayeredPane lp = root.getLayeredPane();
                    final Container content = root.getContentPane();
                    if (content instanceof W32TransparentContentPane) {
                        ((W32TransparentContentPane)content).setTransparent(transparent);
                    }
                    else if (transparent) {
                        final W32TransparentContentPane w32content =
                            new W32TransparentContentPane(content);
                        root.setContentPane(w32content);
                        lp.add(new RepaintTrigger(w32content),
                               JLayeredPane.DRAG_LAYER);
                    }
                    if (transparent && !usingUpdateLayeredWindow(w)) {
                        flags |= WinUser.WS_EX_LAYERED;
                        user.SetWindowLong(hWnd, WinUser.GWL_EXSTYLE, flags);
                    }
                    else if (!transparent && usingUpdateLayeredWindow(w)) {
                        flags &= ~WinUser.WS_EX_LAYERED;
                        user.SetWindowLong(hWnd, WinUser.GWL_EXSTYLE, flags);
                    }
                    setLayersTransparent(w, transparent);
                    setForceHeavyweightPopups(w, transparent);
                    setDoubleBuffered(w, !transparent);
                }
            });
        }

        public void setWindowMask(final Component w, final Shape mask) {
            if (mask instanceof Area && ((Area)mask).isPolygonal()) {
                setMask(w, (Area)mask);
            }
            else {
                super.setWindowMask(w, mask);
            }
        }

        // NOTE: Deletes hrgn after setting the window region
        private void setWindowRegion(final Component w, final HRGN hrgn) {
            whenDisplayable(w, new Runnable() {
                public void run() {
                    final GDI32 gdi = GDI32.INSTANCE;
                    final User32 user = User32.INSTANCE;
                    final HWND hWnd = getHWnd(w);
                    try {
                        user.SetWindowRgn(hWnd, hrgn, true);
                        setForceHeavyweightPopups(getWindow(w), hrgn != null);
                    }
                    finally {
                        gdi.DeleteObject(hrgn);
                    }
                }
            });
        }

        // Take advantage of CreatePolyPolygonalRgn on w32
        private void setMask(final Component w, final Area area) {
            final GDI32 gdi = GDI32.INSTANCE;
            final PathIterator pi = area.getPathIterator(null);
            final int mode = pi.getWindingRule() == PathIterator.WIND_NON_ZERO
                ? WinGDI.WINDING: WinGDI.ALTERNATE;
            final float[] coords = new float[6];
            final List<POINT> points = new ArrayList<POINT>();
            int size = 0;
            final List<Integer> sizes = new ArrayList<Integer>();
            while (!pi.isDone()) {
                final int type = pi.currentSegment(coords);
                if (type == PathIterator.SEG_MOVETO) {
                    size = 1;
                    points.add(new POINT((int)coords[0], (int)coords[1]));
                }
                else if (type == PathIterator.SEG_LINETO) {
                    ++size;
                    points.add(new POINT((int)coords[0], (int)coords[1]));
                }
                else if (type == PathIterator.SEG_CLOSE) {
                    sizes.add(Integer.valueOf(size));
                }
                else
                    throw new RuntimeException("Area is not polygonal: " + area);
                pi.next();
            }
            final POINT[] lppt = (POINT[])new POINT().toArray(points.size());
            final POINT[] pts = points.toArray(new POINT[points.size()]);
            for (int i=0;i < lppt.length;i++) {
                lppt[i].x = pts[i].x;
                lppt[i].y = pts[i].y;
            }
            final int[] counts = new int[sizes.size()];
            for (int i=0;i < counts.length;i++) {
                counts[i] = sizes.get(i).intValue();
            }
            final HRGN hrgn = gdi.CreatePolyPolygonRgn(lppt, counts, counts.length, mode);
            setWindowRegion(w, hrgn);
        }

        protected void setMask(final Component w, final Raster raster) {
            final GDI32 gdi = GDI32.INSTANCE;
            final HRGN region = raster != null
                ? gdi.CreateRectRgn(0, 0, 0, 0) : null;
            if (region != null) {
                final HRGN tempRgn = gdi.CreateRectRgn(0, 0, 0, 0);
                try {
                    RasterRangesUtils.outputOccupiedRanges(raster, new RasterRangesUtils.RangesOutput() {
                        public boolean outputRange(final int x, final int y, final int w, final int h) {
                            final GDI32 gdi = GDI32.INSTANCE;
                            gdi.SetRectRgn(tempRgn, x, y, x + w, y + h);
                            return gdi.CombineRgn(region, region, tempRgn, WinGDI.RGN_OR) != WinGDI.ERROR;
                        }
                    });
                }
                finally {
                    gdi.DeleteObject(tempRgn);
                }
            }
            setWindowRegion(w, region);
        }
    }

    private static class MacWindowUtils extends NativeWindowUtils {
        public boolean isWindowAlphaSupported() {
            return true;
        }

        private OSXMaskingContentPane installMaskingPane(final Window w) {
            OSXMaskingContentPane content;
            if (w instanceof RootPaneContainer) {
                // TODO: replace layered pane instead?
                final RootPaneContainer rpc = (RootPaneContainer)w;
                final Container oldContent = rpc.getContentPane();
                if (oldContent instanceof OSXMaskingContentPane) {
                    content = (OSXMaskingContentPane)oldContent;
                }
                else {
                    content = new OSXMaskingContentPane(oldContent);
                    // TODO: listen for content pane changes
                    rpc.setContentPane(content);
                }
            }
            else {
                final Component oldContent = w.getComponentCount() > 0 ? w.getComponent(0) : null;
                if (oldContent instanceof OSXMaskingContentPane) {
                    content = (OSXMaskingContentPane)oldContent;
                }
                else {
                    content = new OSXMaskingContentPane(oldContent);
                    w.add(content);
                }
            }
            return content;
        }

        /** Note that the property
         * <code>apple.awt.draggableWindowBackground</code> must be set to its
         * final value <em>before</em> the heavyweight peer for the Window is
         * created.  Once {@link Component#addNotify} has been called on the
         * component, causing creation of the heavyweight peer, changing this
         * property has no effect.
         * @see <a href="http://developer.apple.com/technotes/tn2007/tn2196.html#APPLE_AWT_DRAGGABLEWINDOWBACKGROUND">Apple Technote 2007</a>
         */
        public void setWindowTransparent(final Window w, final boolean transparent) {
            final boolean isTransparent = w.getBackground() != null
                && w.getBackground().getAlpha() == 0;
            if (transparent != isTransparent) {
                setBackgroundTransparent(w, transparent, "setWindowTransparent");
            }
        }

        /** Setting this false restores the original setting. */
        private static final String WDRAG = "apple.awt.draggableWindowBackground";
        private void fixWindowDragging(final Window w, final String context) {
            if (w instanceof RootPaneContainer) {
                final JRootPane p = ((RootPaneContainer)w).getRootPane();
                final Boolean oldDraggable = (Boolean)p.getClientProperty(WDRAG);
                if (oldDraggable == null) {
                    p.putClientProperty(WDRAG, Boolean.FALSE);
                    if (w.isDisplayable()) {
                        System.err.println(context + "(): To avoid content dragging, " + context + "() must be called before the window is realized, or " + WDRAG + " must be set to Boolean.FALSE before the window is realized.  If you really want content dragging, set " + WDRAG + " on the window's root pane to Boolean.TRUE before calling " + context + "() to hide this message.");
                    }
                }
            }
        }

        /** Note that the property
         * <code>apple.awt.draggableWindowBackground</code> must be set to its
         * final value <em>before</em> the heavyweight peer for the Window is
         * created.  Once {@link Component#addNotify} has been called on the
         * component, causing creation of the heavyweight peer, changing this
         * property has no effect.
         * @see <a href="http://developer.apple.com/technotes/tn2007/tn2196.html#APPLE_AWT_DRAGGABLEWINDOWBACKGROUND">Apple Technote 2007</a>
         */
        public void setWindowAlpha(final Window w, final float alpha) {
            if (w instanceof RootPaneContainer) {
                final JRootPane p = ((RootPaneContainer)w).getRootPane();
                p.putClientProperty("Window.alpha", Float.valueOf(alpha));
                fixWindowDragging(w, "setWindowAlpha");
            }
            whenDisplayable(w, new Runnable() {

                @Deprecated
                public void run() {
                    final Object peer = w.getPeer();
                    try {
                        peer.getClass().getMethod("setAlpha", float.class)
                                .invoke(peer, alpha);
                    }
                    catch (final Exception e) {}
                }
            });
        }

        protected void setWindowMask(final Component w, final Raster raster) {
            if (raster != null) {
                setWindowMask(w, toShape(raster));
            }
            else {
                setWindowMask(w, new Rectangle(0, 0, w.getWidth(),
                                               w.getHeight()));
            }
        }

        public void setWindowMask(final Component c, final Shape shape) {
            if (c instanceof Window) {
                final Window w = (Window)c;
                final OSXMaskingContentPane content = installMaskingPane(w);
                content.setMask(shape);
                setBackgroundTransparent(w, shape != MASK_NONE, "setWindowMask");
            }
            else {
                // not yet implemented
            }
        }

        /** Mask out unwanted pixels and ensure background gets cleared.
         * @author Olivier Chafik
         */
        private static class OSXMaskingContentPane extends JPanel {
    		private static final long serialVersionUID = 1L;
            private Shape shape;

            public OSXMaskingContentPane(final Component oldContent) {
                super(new BorderLayout());
                if (oldContent != null) {
                    add(oldContent, BorderLayout.CENTER);
                }
            }

            public void setMask(final Shape shape) {
                this.shape = shape;
                repaint();
            }

            public void paint(final Graphics graphics) {
                Graphics2D g = (Graphics2D)graphics.create();
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.dispose();
                if (shape != null) {
                    g = (Graphics2D)graphics.create();
                    g.setClip(shape);
                    super.paint(g);
                    g.dispose();
                }
                else {
                    super.paint(graphics);
                }
            }
        }

        private void setBackgroundTransparent(final Window w, final boolean transparent, final String context) {
            final JRootPane rp = w instanceof RootPaneContainer
                ? ((RootPaneContainer)w).getRootPane() : null;
            if (transparent) {
                if (rp != null) {
                    rp.putClientProperty(TRANSPARENT_OLD_BG, w.getBackground());
                }
                w.setBackground(new Color(0,0,0,0));
            }
            else {
                if (rp != null) {
                    Color bg = (Color)rp.getClientProperty(TRANSPARENT_OLD_BG);
                    // If the old bg is a
                    // apple.laf.CColorPaintUIResource, the window's
                    // transparent state will not change
                    if (bg != null) {
                        bg = new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), bg.getAlpha());
                    }
                    w.setBackground(bg);
                    rp.putClientProperty(TRANSPARENT_OLD_BG, null);
                }
                else {
                    w.setBackground(null);
                }
            }
            fixWindowDragging(w, context);
        }
    }
    private static class X11WindowUtils extends NativeWindowUtils {
        private static Pixmap createBitmap(final Display dpy,
                                           final X11.Window win,
                                           final Raster raster) {
            final X11 x11 = X11.INSTANCE;
            final Rectangle bounds = raster.getBounds();
            final int width = bounds.x + bounds.width;
            final int height = bounds.y + bounds.height;
            final Pixmap pm = x11.XCreatePixmap(dpy, win, width, height, 1);
            final GC gc = x11.XCreateGC(dpy, pm, new NativeLong(0), null);
            if (gc == null)
                return null;
            x11.XSetForeground(dpy, gc, new NativeLong(0));
            x11.XFillRectangle(dpy, pm, gc, 0, 0, width, height);
            final List<Rectangle> rlist = new ArrayList<Rectangle>();
            try {
                RasterRangesUtils.outputOccupiedRanges(raster, new RasterRangesUtils.RangesOutput() {
                    public boolean outputRange(final int x, final int y, final int w, final int h) {
                        rlist.add(new Rectangle(x, y, w, h));
                        return true;
                    }
                });
                final X11.XRectangle[] rects = (X11.XRectangle[])
                    new X11.XRectangle().toArray(rlist.size());
                for (int i=0;i < rects.length;i++) {
                    final Rectangle r = rlist.get(i);
                    rects[i].x = (short)r.x;
                    rects[i].y = (short)r.y;
                    rects[i].width = (short)r.width;
                    rects[i].height = (short)r.height;
                    // Optimization: write directly to native memory
                    final Pointer p = rects[i].getPointer();
                    p.setShort(0, (short)r.x);
                    p.setShort(2, (short)r.y);
                    p.setShort(4, (short)r.width);
                    p.setShort(6, (short)r.height);
                    rects[i].setAutoSynch(false);
                    // End optimization
                }
                final int UNMASKED = 1;
                x11.XSetForeground(dpy, gc, new NativeLong(UNMASKED));
                x11.XFillRectangles(dpy, pm, gc, rects, rects.length);
            }
            finally {
                x11.XFreeGC(dpy, gc);
            }
            return pm;
        }

        private boolean didCheck;
        private long[] alphaVisualIDs = {};

        public boolean isWindowAlphaSupported() {
            return getAlphaVisualIDs().length > 0;
        }

        private static long getVisualID(final GraphicsConfiguration config) {
            // Use reflection to call
            // X11GraphicsConfig.getVisual
            try {
                final Object o = config.getClass()
                    .getMethod("getVisual", (Class[])null)
                    .invoke(config, (Object[])null);
                return ((Number)o).longValue();
            }
            catch (final Exception e) {
                e.printStackTrace();
                return -1;
            }
        }

        /** Return the default graphics configuration. */
        public GraphicsConfiguration getAlphaCompatibleGraphicsConfiguration() {
            if (isWindowAlphaSupported()) {
                final GraphicsEnvironment env =
                    GraphicsEnvironment.getLocalGraphicsEnvironment();
                final GraphicsDevice[] devices = env.getScreenDevices();
                for (int i = 0; i < devices.length; i++) {
                    final GraphicsConfiguration[] configs =
                        devices[i].getConfigurations();
                    for (int j = 0; j < configs.length; j++) {
                        final long visualID = getVisualID(configs[j]);
                        final long[] ids = getAlphaVisualIDs();
                        for (int k = 0; k < ids.length; k++) {
                            if (visualID == ids[k])
                                return configs[j];
                        }
                    }
                }
            }
            return super.getAlphaCompatibleGraphicsConfiguration();
        }

        /**
         * Return the visual ID of the visual which supports an alpha
         * channel.
         */
        private synchronized long[] getAlphaVisualIDs() {
            if (didCheck)
                return alphaVisualIDs;
            didCheck = true;
            final X11 x11 = X11.INSTANCE;
            final Display dpy = x11.XOpenDisplay(null);
            if (dpy == null)
                return alphaVisualIDs;
            XVisualInfo info = null;
            try {
                final int screen = x11.XDefaultScreen(dpy);
                final XVisualInfo template = new XVisualInfo();
                template.screen = screen;
                template.depth = 32;
                template.c_class = X11.TrueColor;
                final NativeLong mask = new NativeLong(X11.VisualScreenMask
                                                 | X11.VisualDepthMask
                                                 | X11.VisualClassMask);
                final IntByReference pcount = new IntByReference();
                info = x11.XGetVisualInfo(dpy, mask, template, pcount);
                if (info != null) {
                    final List<X11.VisualID> list = new ArrayList<X11.VisualID>();
                    final XVisualInfo[] infos =
                        (XVisualInfo[])info.toArray(pcount.getValue());
                    for (int i = 0; i < infos.length; i++) {
                        final XRenderPictFormat format =
                            X11.Xrender.INSTANCE.XRenderFindVisualFormat(dpy,
                                                                         infos[i].visual);
                        if (format.type == X11.Xrender.PictTypeDirect
                            && format.direct.alphaMask != 0) {
                            list.add(infos[i].visualid);
                        }
                    }
                    alphaVisualIDs = new long[list.size()];
                    for (int i=0;i < alphaVisualIDs.length;i++) {
                        alphaVisualIDs[i] = ((Number)list.get(i)).longValue();
                    }
                    return alphaVisualIDs;
                }
            }
            finally {
                if (info != null) {
                    x11.XFree(info.getPointer());
                }
                x11.XCloseDisplay(dpy);
            }
            return alphaVisualIDs;
        }

        private static X11.Window getContentWindow(final Window w, final X11.Display dpy,
                                                   X11.Window win, final Point offset) {
            if (w instanceof Frame && !((Frame)w).isUndecorated()
                || w instanceof Dialog && !((Dialog)w).isUndecorated()) {
                final X11 x11 = X11.INSTANCE;
                final X11.WindowByReference rootp = new X11.WindowByReference();
                final X11.WindowByReference parentp = new X11.WindowByReference();
                final PointerByReference childrenp = new PointerByReference();
                final IntByReference countp = new IntByReference();
                x11.XQueryTree(dpy, win, rootp, parentp, childrenp, countp);
                final Pointer p = childrenp.getValue();
                final int[] ids = p.getIntArray(0, countp.getValue());
                for (final int id : ids) {
                    // TODO: more verification of correct window?
                    final X11.Window child = new X11.Window(id);
                    final X11.XWindowAttributes xwa = new X11.XWindowAttributes();
                    x11.XGetWindowAttributes(dpy, child, xwa);
                    offset.x = -xwa.x;
                    offset.y = -xwa.y;
                    win = child;
                    break;
                }
                if (p != null) {
                    x11.XFree(p);
                }
            }
            return win;
        }

        private static X11.Window getDrawable(final Component w) {
            final int id = (int)Native.getComponentID(w);
            if (id == X11.None)
                return null;
            return new X11.Window(id);
        }

        private static final long OPAQUE = 0xFFFFFFFFL;
        private static final String OPACITY = "_NET_WM_WINDOW_OPACITY";

        public void setWindowAlpha(final Window w, final float alpha) {
            if (!isWindowAlphaSupported())
                throw new UnsupportedOperationException("This X11 display does not provide a 32-bit visual");
            final Runnable action = new Runnable() {
                public void run() {
                    final X11 x11 = X11.INSTANCE;
                    final Display dpy = x11.XOpenDisplay(null);
                    if (dpy == null)
                        return;
                    try {
                        final X11.Window win = getDrawable(w);
                        if (alpha == 1f) {
                            x11.XDeleteProperty(dpy, win,
                                                x11.XInternAtom(dpy, OPACITY,
                                                                false));
                        }
                        else {
                            final int opacity = (int)((long)(alpha * OPAQUE) & 0xFFFFFFFF);
                            final IntByReference patom = new IntByReference(opacity);
                            x11.XChangeProperty(dpy, win,
                                                x11.XInternAtom(dpy, OPACITY,
                                                                false),
                                                X11.XA_CARDINAL, 32,
                                                X11.PropModeReplace,
                                                patom.getPointer(), 1);
                        }
                    }
                    finally {
                        x11.XCloseDisplay(dpy);
                    }
                }
            };
            whenDisplayable(w, action);
        }

        private class X11TransparentContentPane extends TransparentContentPane {
    		private static final long serialVersionUID = 1L;

            public X11TransparentContentPane(final Container oldContent) {
                super(oldContent);
            }

            private Memory buffer;
            private int[] pixels;
            private final int[] pixel = new int[4];
            // Painting directly to the original Graphics
            // fails to properly composite unless the destination
            // is pure black.  Too bad.
			protected void paintDirect(final BufferedImage buf, final Rectangle bounds) {
                final Window window = SwingUtilities.getWindowAncestor(this);
                final X11 x11 = X11.INSTANCE;
                final X11.Display dpy = x11.XOpenDisplay(null);
                X11.Window win = getDrawable(window);
                final Point offset = new Point();
                win = getContentWindow(window, dpy, win, offset);
                final X11.GC gc = x11.XCreateGC(dpy, win, new NativeLong(0), null);

                final Raster raster = buf.getData();
                final int w = bounds.width;
                final int h = bounds.height;
                if (buffer == null || buffer.size() != w*h*4) {
                    buffer = new Memory(w*h*4);
                    pixels = new int[w*h];
                }
                for (int y=0;y<h;y++) {
                    for (int x=0;x < w;x++) {
                        raster.getPixel(x, y, pixel);
                        final int alpha = pixel[3]&0xFF;
                        final int red = pixel[2]&0xFF;
                        final int green = pixel[1]&0xFF;
                        final int blue = pixel[0]&0xFF;
                        // TODO: use visual RGB masks to position bits
                        // This layout (ABGR) works empirically
                        pixels[y*w + x] = alpha<<24|blue<<16|green<<8|red;
                    }
                }
                final X11.XWindowAttributes xwa = new X11.XWindowAttributes();
                x11.XGetWindowAttributes(dpy, win, xwa);
                final X11.XImage image =
                    x11.XCreateImage(dpy, xwa.visual, 32, X11.ZPixmap,
                                     0, buffer, w, h, 32, w * 4);
                buffer.write(0, pixels, 0, pixels.length);
                offset.x += bounds.x;
                offset.y += bounds.y;
                x11.XPutImage(dpy, win, gc, image, 0, 0, offset.x, offset.y, w, h);

                x11.XFree(image.getPointer());
                x11.XFreeGC(dpy, gc);
                x11.XCloseDisplay(dpy);
            }
        }

        public void setWindowTransparent(final Window w,
                                         final boolean transparent) {
            if (!(w instanceof RootPaneContainer))
                throw new IllegalArgumentException("Window must be a RootPaneContainer");
            if (!isWindowAlphaSupported())
                throw new UnsupportedOperationException("This X11 display does not provide a 32-bit visual");
            if (!w.getGraphicsConfiguration()
                .equals(getAlphaCompatibleGraphicsConfiguration()))
                throw new IllegalArgumentException("Window GraphicsConfiguration '" + w.getGraphicsConfiguration() + "' does not support transparency");
            final boolean isTransparent = w.getBackground() != null
                && w.getBackground().getAlpha() == 0;
            if (transparent == isTransparent)
                return;
            whenDisplayable(w, new Runnable() {
                public void run() {
                    final JRootPane root = ((RootPaneContainer)w).getRootPane();
                    final JLayeredPane lp = root.getLayeredPane();
                    final Container content = root.getContentPane();
                    if (content instanceof X11TransparentContentPane) {
                        ((X11TransparentContentPane)content).setTransparent(transparent);
                    }
                    else if (transparent) {
                        final X11TransparentContentPane x11content =
                            new X11TransparentContentPane(content);
                        root.setContentPane(x11content);
                        lp.add(new RepaintTrigger(x11content),
                               JLayeredPane.DRAG_LAYER);
                    }
                    setLayersTransparent(w, transparent);
                    setForceHeavyweightPopups(w, transparent);
                    setDoubleBuffered(w, !transparent);
                }
            });
        }

        private interface PixmapSource {
            Pixmap getPixmap(Display dpy, X11.Window win);
        }

        private void setWindowShape(final Window w, final PixmapSource src) {
            final Runnable action = new Runnable() {
                public void run() {
                    final X11 x11 = X11.INSTANCE;
                    final Display dpy = x11.XOpenDisplay(null);
                    if (dpy == null)
                        return;
                    Pixmap pm = null;
                    try {
                        final X11.Window win = getDrawable(w);
                        pm = src.getPixmap(dpy, win);
                        final Xext ext = Xext.INSTANCE;
                        ext.XShapeCombineMask(dpy, win, X11.Xext.ShapeBounding,
                                              0, 0, pm == null ? Pixmap.None : pm,
                                              X11.Xext.ShapeSet);
                    }
                    finally {
                        if (pm != null) {
                            x11.XFreePixmap(dpy, pm);
                        }
                        x11.XCloseDisplay(dpy);
                    }
                    setForceHeavyweightPopups(getWindow(w), pm != null);
                }
            };
            whenDisplayable(w, action);
        }

        protected void setMask(final Component w, final Raster raster) {
            setWindowShape(getWindow(w), new PixmapSource() {
                public Pixmap getPixmap(final Display dpy, final X11.Window win) {
                    return raster != null ? createBitmap(dpy, win, raster) : null;
                }
            });
        }
    }

    /**
     * Applies the given mask to the given window. Does nothing if the
     * operation is not supported.  The mask is treated as a bitmap and
     * ignores transparency.
     */
    public static void setWindowMask(final Window w, final Shape mask) {
        getInstance().setWindowMask(w, mask);
    }

    /**
     * Applies the given mask to the given heavyweight component. Does nothing
     * if the operation is not supported.  The mask is treated as a bitmap and
     * ignores transparency.
     */
    public static void setComponentMask(final Component c, final Shape mask) {
        getInstance().setWindowMask(c, mask);
    }

    /**
     * Applies the given mask to the given window. Does nothing if the
     * operation is not supported.  The mask is treated as a bitmap and
     * ignores transparency.
     */
    public static void setWindowMask(final Window w, final Icon mask) {
        getInstance().setWindowMask(w, mask);
    }

    /** Indicate a window can have a global alpha setting. */
    public static boolean isWindowAlphaSupported() {
        return getInstance().isWindowAlphaSupported();
    }

    /**
     * Returns a {@link GraphicsConfiguration} comptible with alpha
     * compositing.
     */
    public static GraphicsConfiguration getAlphaCompatibleGraphicsConfiguration() {
        return getInstance().getAlphaCompatibleGraphicsConfiguration();
    }

    /**
     * Set the overall window transparency. An alpha of 1.0 is fully
     * opaque, 0.0 fully transparent. The alpha level is applied equally
     * to all window pixels.<p>
     * NOTE: Windows requires that <code>sun.java2d.noddraw=true</code>
     * in order for alpha to work.<p>
     * NOTE: On OSX, the property
     * <code>apple.awt.draggableWindowBackground</code> must be set to its
     * final value <em>before</em> the heavyweight peer for the Window is
     * created.  Once {@link Component#addNotify} has been called on the
     * component, causing creation of the heavyweight peer, changing this
     * property has no effect.
     * @see <a href="http://developer.apple.com/technotes/tn2007/tn2196.html#APPLE_AWT_DRAGGABLEWINDOWBACKGROUND">Apple Technote 2007</a>
     */
    public static void setWindowAlpha(final Window w, final float alpha) {
        getInstance().setWindowAlpha(w, Math.max(0f, Math.min(alpha, 1f)));
    }

    /**
     * Set the window to be transparent. Only explicitly painted pixels
     * will be non-transparent. All pixels will be composited with
     * whatever is under the window using their alpha values.
     *
     * On OSX, the property <code>apple.awt.draggableWindowBackground</code>
     * must be set to its final value <em>before</em> the heavyweight peer for
     * the Window is created.  Once {@link Component#addNotify} has been
     * called on the component, causing creation of the heavyweight peer,
     * changing this property has no effect.
     * @see <a href="http://developer.apple.com/technotes/tn2007/tn2196.html#APPLE_AWT_DRAGGABLEWINDOWBACKGROUND">Apple Technote 2007</a>
     */
    public static void setWindowTransparent(final Window w, final boolean transparent) {
        getInstance().setWindowTransparent(w, transparent);
    }
}
