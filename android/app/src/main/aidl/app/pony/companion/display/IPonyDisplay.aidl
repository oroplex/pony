package app.pony.companion.display;

interface IPonyDisplay {
    int ensureDisplay(int width, int height, int density);
    boolean trusted();
    boolean launch(String component, int displayId);
    boolean pressKey(int displayId, int keyCode);
    void releaseDisplay();
    void destroy();
}
