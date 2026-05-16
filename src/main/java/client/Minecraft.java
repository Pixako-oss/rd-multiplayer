package client;

import client.hud.Chat;
import client.hud.Crosshair;
import client.hud.Info;
import client.level.Chunk;
import client.level.Level;
import client.level.LevelRenderer;
import client.net.PlayerManager;
import global.Packets;
import client.net.SocketClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Properties;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.util.glu.GLU.gluPerspective;
import static org.lwjgl.util.glu.GLU.gluPickMatrix;

public class Minecraft implements Runnable {
    public static Minecraft mc;

    public String username;

    public static final String GIT_HASH;
    static {
        String hash = "unknown";
        try (InputStream in = Minecraft.class.getResourceAsStream("/git.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                hash = props.getProperty("git.commit", hash);
            }
        } catch (Exception ignored) {}
        GIT_HASH = hash;
    }

    public long rtt;

    private final Timer timer = new Timer(60);

    public Level level;
    public LevelRenderer levelRenderer;
    public Player player;
    private FontRenderer font;
    private Font minecraftFont;
    public Chat chat;
    public SocketClient socket;
    public Thread socketThread;
    public PlayerManager playerManager;

    private Crosshair crosshair;
    private Info info;
    public int fps;

    private final FloatBuffer fogColor = BufferUtils.createFloatBuffer(4);

    public final int width = 1280;
    private final int height = 720;

    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
    private final IntBuffer selectBuffer = BufferUtils.createIntBuffer(2000);
    private HitResult hitResult;

    public int pendingWidth = -1;
    public int pendingHeight = -1;
    public int pendingDepth = -1;
    public byte[] pendingBlocks = null;
    public boolean levelUpdatePending = false;

    public volatile String loadingText = "";
    public volatile Color loadingColor = Color.WHITE;

    private void applyPendingLevel() {
        if (!levelUpdatePending) return;
        this.level = new client.level.Level(pendingWidth, pendingHeight, pendingDepth);
        this.level.loadLevel(pendingWidth, pendingHeight, pendingDepth, pendingBlocks);
        this.levelRenderer = new client.level.LevelRenderer(this.level);
        this.player = new Player(this.level);

        this.levelRenderer.rebuildAll();

        levelUpdatePending = false;
        System.out.println("Level loaded from server!");
    }

    public Minecraft(String ip, int port, String username) throws IOException {
        mc = this;
        this.username = username;
        this.socket = new SocketClient(ip, port, username);
        this.socketThread = new Thread(socket);
        this.playerManager = new PlayerManager();
    }

    public void init() throws LWJGLException {
        this.fogColor.put(new float[]{
                14 / 255.0F,
                11 / 255.0F,
                10 / 255.0F,
                255 / 255.0F
        }).flip();

        Display.setDisplayMode(new DisplayMode(this.width, this.height));
        Display.setTitle("rd-multiplayer " + GIT_HASH);
        Display.setVSyncEnabled(true);

        Display.create();
        Keyboard.create();
        Mouse.create();

        glEnable(GL_TEXTURE_2D);
        glShadeModel(GL_SMOOTH);
        glClearColor(0.5F, 0.8F, 1.0F, 0.0F);
        glClearDepth(1.0);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDepthFunc(GL_LEQUAL);

        try (InputStream in = FontRenderer.class.getResourceAsStream("/client/fonts/Minecraft.ttf")) {
            minecraftFont = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(16f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        font = new FontRenderer(minecraftFont);

        crosshair = new Crosshair(16, "/client/textures/crosshair.png");
        info = new Info(font);
        chat = new Chat(font, 50, 0, height - 150 - 16, 500, 150);



        Mouse.setGrabbed(true);
    }

    public void destroy() {
        Mouse.destroy();
        Keyboard.destroy();
        Display.destroy();
        System.exit(0);
    }

    @Override
    public void run() {
        socketThread.start();

        try {
            init();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e, "Failed to start Minecraft", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        while (!levelUpdatePending) {
            renderLoadingScreen();
            try { Thread.sleep(16); } catch (InterruptedException ignored) {}
        }

        applyPendingLevel();
        keepAlive();

        int frames = 0;
        long lastTime = System.currentTimeMillis();

        try {
            while (!Display.isCloseRequested()) {
                this.timer.advanceTime();

                for (int i = 0; i < this.timer.ticks; ++i) {
                    tick();
                }

                render(this.timer.partialTicks);

                frames++;

                while (System.currentTimeMillis() >= lastTime + 1000L) {
                    System.out.println(frames + " fps, " + Chunk.updates);
                    fps = frames;
                    Chunk.updates = 0;
                    lastTime += 1000L;
                    frames = 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            destroy();
        }
    }


    private void tick() throws IOException {
        int[] update;
        while ((update = SocketClient.pendingBlocks.poll()) != null) {
            if (this.level != null)
                this.level.setTile(update[0], update[1], update[2], update[3]);
        }

        this.player.tick();
    }

    private void moveCameraToPlayer(float partialTicks) {
        Player player = this.player;

        glTranslatef(0.0f, 0.0f, -0.3f);

        glRotatef(player.xRotation, 1.0f, 0.0f, 0.0f);
        glRotatef(player.yRotation, 0.0f, 1.0f, 0.0f);

        double x = this.player.prevX + (this.player.x - this.player.prevX) * partialTicks;
        double y = this.player.prevY + (this.player.y - this.player.prevY) * partialTicks;
        double z = this.player.prevZ + (this.player.z - this.player.prevZ) * partialTicks;

        glTranslated(-x, -y, -z);
    }


    private void setupCamera(float partialTicks) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        gluPerspective(70, width / (float) height, 0.05F, 1000);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        moveCameraToPlayer(partialTicks);
    }

    private void setupPickCamera(float partialTicks, int x, int y) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        this.viewportBuffer.clear();

        glGetInteger(GL_VIEWPORT, this.viewportBuffer);

        this.viewportBuffer.flip();
        this.viewportBuffer.limit(16);

        gluPickMatrix(x, y, 5.0f, 5.0f, this.viewportBuffer);
        gluPerspective(70.0f, this.width / (float) this.height, 0.05f, 1000.0f);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        moveCameraToPlayer(partialTicks);
    }

    private void pick(float partialTicks) {
        this.selectBuffer.clear();

        glSelectBuffer(this.selectBuffer);
        glRenderMode(GL_SELECT);

        this.setupPickCamera(partialTicks, this.width / 2, this.height / 2);

        this.levelRenderer.pick(this.player);

        this.selectBuffer.flip();
        this.selectBuffer.limit(this.selectBuffer.capacity());

        long closest = 0L;
        int[] names = new int[10];
        int hitNameCount = 0;

        int hits = glRenderMode(GL_RENDER);
        for (int hitIndex = 0; hitIndex < hits; hitIndex++) {

            int nameCount = this.selectBuffer.get();
            long minZ = this.selectBuffer.get();
            this.selectBuffer.get();

            if (minZ < closest || hitIndex == 0) {
                closest = minZ;
                hitNameCount = nameCount;

                for (int nameIndex = 0; nameIndex < nameCount; nameIndex++) {
                    names[nameIndex] = this.selectBuffer.get();
                }
            } else {
                for (int nameIndex = 0; nameIndex < nameCount; ++nameIndex) {
                    this.selectBuffer.get();
                }
            }
        }

        if (hitNameCount > 0) {
            this.hitResult = new HitResult(names[0], names[1], names[2], names[3], names[4]);
        } else {
            this.hitResult = null;
        }
    }


    private void render(float partialTicks) throws IOException {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (!levelUpdatePending && level.getWidth() > 0 && level.getHeight() > 0 && level.getDepth() > 0) {
            float motionX = Mouse.getDX();
            float motionY = Mouse.getDY();
            this.player.turn(motionX, motionY);

            pick(partialTicks);

            while (Mouse.next()) {
                if (Mouse.getEventButtonState() && hitResult != null) {
                    if (Mouse.getEventButton() == 0)
                        SocketClient.sendBlock(Packets.BLOCK_BREAK, hitResult.x, hitResult.y, hitResult.z);
                    if (Mouse.getEventButton() == 1) {
                        int x = hitResult.x;
                        int y = hitResult.y;
                        int z = hitResult.z;
                        if (hitResult.face == 0) y--;
                        if (hitResult.face == 1) y++;
                        if (hitResult.face == 2) z--;
                        if (hitResult.face == 3) z++;
                        if (hitResult.face == 4) x--;
                        if (hitResult.face == 5) x++;

                        float pMinX = (float) (player.x - player.width);
                        float pMaxX = (float) (player.x + player.width);
                        float pMinY = (float) (player.y - player.height);
                        float pMaxY = (float) (player.y + player.height);
                        float pMinZ = (float) (player.z - player.width);
                        float pMaxZ = (float) (player.z + player.width);

                        float bMinX = x;
                        float bMaxX = x + 1;
                        float bMinY = y;
                        float bMaxY = y + 1;
                        float bMinZ = z;
                        float bMaxZ = z + 1;

                        boolean intersects = pMaxX > bMinX && pMinX < bMaxX && pMaxY > bMinY && pMinY < bMaxY && pMaxZ > bMinZ && pMinZ < bMaxZ;

                        if (intersects) return;

                        SocketClient.sendBlock(Packets.BLOCK_PLACE, x, y, z);
                    }

                }
            }

            setupCamera(partialTicks);

            glEnable(GL_FOG);
            glFogi(GL_FOG_MODE, GL_LINEAR);
            glFogf(GL_FOG_START, -10);
            glFogf(GL_FOG_END, 20);
            glFog(GL_FOG_COLOR, this.fogColor);
            glDisable(GL_FOG);

            levelRenderer.render(0);
            glEnable(GL_FOG);
            levelRenderer.render(1);
            levelRenderer.renderPlayers(Minecraft.mc.getPlayerManager());
            levelRenderer.renderNameTags(this.playerManager, this.player, this.font);
            glDisable(GL_TEXTURE_2D);

            if (hitResult != null)
                levelRenderer.renderHit(hitResult);

            glDisable(GL_FOG);

            crosshair.render(width, height);
            info.render(width, height);
            chat.render(width, height);

        } else {
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }

        Display.update();
    }

    private void keepAlive() {
        Thread keepAliveThread = new Thread(() -> {
            while (true) {
                try {
                    if (socket.isConnected()) {
                        long timestamp = System.currentTimeMillis();
                        SocketClient.sendKeepalive(timestamp);
                    }
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, "KeepAliveThread");
        keepAliveThread.setDaemon(true);
        keepAliveThread.start();
    }

    private int loadingBackground = -1;

    private void renderLoadingScreen() {
        if (loadingBackground == -1) {
            loadingBackground = Textures.loadTexture("/client/textures/background.png", GL_NEAREST);
        }

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glColor4f(1f, 1f, 1f, 1f);
        Textures.bind(loadingBackground);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(0, 0);
        glTexCoord2f(1, 0); glVertex2f(width, 0);
        glTexCoord2f(1, 1); glVertex2f(width, height);
        glTexCoord2f(0, 1); glVertex2f(0, height);
        glEnd();

        int textWidth = font.getStringWidth(loadingText);
        int textHeight = font.getStringHeight();
        int tx = (width  / 2) - (textWidth  / 2);
        int ty = (height / 2) - (textHeight / 2);
        glColor4f(1f, 1f, 1f, 1f);
        font.drawString(loadingText, tx, ty, loadingColor, true);

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);

        Display.update();
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public Level getLevel() {return level;}
}