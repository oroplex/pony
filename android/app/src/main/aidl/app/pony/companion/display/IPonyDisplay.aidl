package app.pony.companion.display;

interface IPonyDisplay {
    int ensureDisplay(int width, int height, int density);
    boolean trusted();
    boolean launch(String component, int displayId);
    boolean pressKey(int displayId, int keyCode);
    String topPackage(int displayId);
    boolean setImePolicy(int displayId, int policy);
    void releaseDisplay();
    void destroy();
}
