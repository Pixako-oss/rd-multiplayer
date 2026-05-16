package client.level;

import client.*;
import client.phys.AABB;
import client.net.PlayerManager;
import org.lwjgl.BufferUtils;

import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

public class LevelRenderer implements LevelListener {

    private static final int CHUNK_SIZE = 16;

    private final Tessellator tessellator;
    private final Level level;
    private final Chunk[] chunks;

    private final int chunkAmountX;
    private final int chunkAmountY;
    private final int chunkAmountZ;

    public LevelRenderer(Level level) {
        this.tessellator = new Tessellator();
        this.level = level;

        this.chunkAmountX = level.width / CHUNK_SIZE;
        this.chunkAmountY = level.depth / CHUNK_SIZE;
        this.chunkAmountZ = level.height / CHUNK_SIZE;

        this.chunks = new Chunk[this.chunkAmountX * this.chunkAmountY * this.chunkAmountZ];

        for (int x = 0; x < this.chunkAmountX; x++) {
            for (int y = 0; y < this.chunkAmountY; y++) {
                for (int z = 0; z < this.chunkAmountZ; z++) {
                    int minChunkX = x * CHUNK_SIZE;
                    int minChunkY = y * CHUNK_SIZE;
                    int minChunkZ = z * CHUNK_SIZE;

                    int maxChunkX = Math.min(level.width,  (x + 1) * CHUNK_SIZE);
                    int maxChunkY = Math.min(level.depth,  (y + 1) * CHUNK_SIZE);
                    int maxChunkZ = Math.min(level.height, (z + 1) * CHUNK_SIZE);

                    this.chunks[(x + y * this.chunkAmountX) * this.chunkAmountZ + z] =
                            new Chunk(level, minChunkX, minChunkY, minChunkZ, maxChunkX, maxChunkY, maxChunkZ);
                }
            }
        }

        level.addListener(this);
    }

    public void render(int layer) {
        Frustum frustum = Frustum.getFrustum();

        Chunk.rebuiltThisFrame = 0;

        for (Chunk chunk : this.chunks) {

            if (frustum.cubeInFrustum(chunk.boundingBox)) {

                chunk.render(layer);
            }
        }
    }

    public void setDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        minX /= CHUNK_SIZE;
        minY /= CHUNK_SIZE;
        minZ /= CHUNK_SIZE;
        maxX /= CHUNK_SIZE;
        maxY /= CHUNK_SIZE;
        maxZ /= CHUNK_SIZE;

        minX = Math.max(minX, 0);
        minY = Math.max(minY, 0);
        minZ = Math.max(minZ, 0);

        maxX = Math.min(maxX, this.chunkAmountX - 1);
        maxY = Math.min(maxY, this.chunkAmountY - 1);
        maxZ = Math.min(maxZ, this.chunkAmountZ - 1);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Chunk chunk = this.chunks[(x + y * this.chunkAmountX) * this.chunkAmountZ + z];

                    chunk.setDirty();
                }
            }
        }
    }

    public void pick(Player player) {
        float radius = 3.0F;
        AABB boundingBox = player.boundingBox.grow(radius, radius, radius);

        int minX = (int) boundingBox.minX;
        int maxX = (int) (boundingBox.maxX + 1.0f);
        int minY = (int) boundingBox.minY;
        int maxY = (int) (boundingBox.maxY + 1.0f);
        int minZ = (int) boundingBox.minZ;
        int maxZ = (int) (boundingBox.maxZ + 1.0f);

        glInitNames();
        for (int x = minX; x < maxX; x++) {
            glPushName(x);
            for (int y = minY; y < maxY; y++) {
                glPushName(y);
                for (int z = minZ; z < maxZ; z++) {
                    glPushName(z);

                    if (this.level.isSolidTile(x, y, z)) {

                        glPushName(0);

                        for (int face = 0; face < 6; face++) {

                            glPushName(face);

                            this.tessellator.init();
                            Tile.rock.renderFace(this.tessellator, x, y, z, face);
                            this.tessellator.flush();

                            glPopName();
                        }
                        glPopName();
                    }
                    glPopName();
                }
                glPopName();
            }
            glPopName();
        }
    }

    public void renderHit(HitResult hitResult) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_CURRENT_BIT);
        glColor4f(1.0f, 1.0f, 1.0f, (float) Math.sin(System.currentTimeMillis() / 100.0) * 0.2f + 0.4f);

        this.tessellator.init();
        Tile.rock.renderFace(this.tessellator, hitResult.x, hitResult.y, hitResult.z, hitResult.face);
        this.tessellator.flush();

        glDisable(GL_BLEND);
    }

    public void renderPlayers(PlayerManager playerManager) {
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        glDisable(GL_FOG);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        for (Map.Entry<String, Position> entry : playerManager.getPlayers().entrySet()) {
            Position pos = entry.getValue();
            glPushMatrix();
            glTranslatef((float) pos.x, (float) pos.y - 1.62f, (float) pos.z);
            glRotatef(-pos.yaw, 0f, 1f, 0f);
            this.tessellator.init();
            renderPlayerModel();
            this.tessellator.flush();
            glPopMatrix();
        }

        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glEnable(GL_FOG);
        glEnable(GL_TEXTURE_2D);
    }

    private void renderPlayerModel() {
        renderBox(-0.25f, 1.25f, -0.25f,  0.25f, 1.75f,  0.25f,  0.85f, 0.65f, 0.50f);
        renderBox(-0.25f, 0.50f, -0.125f, 0.25f, 1.25f,  0.125f, 0.22f, 0.40f, 0.75f);
        renderBox( 0.00f, 0.00f, -0.125f, 0.25f, 0.50f,  0.125f, 0.15f, 0.25f, 0.55f);
        renderBox(-0.25f, 0.00f, -0.125f, 0.00f, 0.50f,  0.125f, 0.15f, 0.25f, 0.55f);
        renderBox( 0.25f, 0.50f, -0.125f, 0.50f, 1.25f,  0.125f, 0.85f, 0.65f, 0.50f);
        renderBox(-0.50f, 0.50f, -0.125f,-0.25f, 1.25f,  0.125f, 0.85f, 0.65f, 0.50f);
    }

    private void renderBox(float x0, float y0, float z0, float x1, float y1, float z1,
                           float r, float g, float b) {
        this.tessellator.color(r, g, b);

        this.tessellator.vertex(x0, y0, z0);
        this.tessellator.vertex(x1, y0, z0);
        this.tessellator.vertex(x1, y0, z1);
        this.tessellator.vertex(x0, y0, z1);

        this.tessellator.vertex(x0, y1, z0);
        this.tessellator.vertex(x1, y1, z0);
        this.tessellator.vertex(x1, y1, z1);
        this.tessellator.vertex(x0, y1, z1);

        this.tessellator.vertex(x0, y0, z0);
        this.tessellator.vertex(x1, y0, z0);
        this.tessellator.vertex(x1, y1, z0);
        this.tessellator.vertex(x0, y1, z0);

        this.tessellator.vertex(x0, y0, z1);
        this.tessellator.vertex(x1, y0, z1);
        this.tessellator.vertex(x1, y1, z1);
        this.tessellator.vertex(x0, y1, z1);

        this.tessellator.vertex(x0, y0, z0);
        this.tessellator.vertex(x0, y1, z0);
        this.tessellator.vertex(x0, y1, z1);
        this.tessellator.vertex(x0, y0, z1);

        this.tessellator.vertex(x1, y0, z0);
        this.tessellator.vertex(x1, y1, z0);
        this.tessellator.vertex(x1, y1, z1);
        this.tessellator.vertex(x1, y0, z1);
    }
    public void renderNameTags(PlayerManager playerManager, Player localPlayer, FontRenderer fontRenderer) {
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_FOG);

        glDepthMask(false);
        glDisable(GL_CULL_FACE);

        for (Map.Entry<String, client.Position> entry : playerManager.getPlayers().entrySet()) {
            String name = entry.getKey();
            client.Position pos = entry.getValue();

            glPushMatrix();

            glTranslated(pos.x, pos.y + 0.7D, pos.z);

            glRotatef(-localPlayer.yRotation, 0.0F, 1.0F, 0.0F);
            glRotatef(localPlayer.xRotation, 1.0F, 0.0F, 0.0F);

            float scale = 0.015F;
            glScalef(scale, -scale, scale);

            int textWidth = fontRenderer.getStringWidth(name);
            int textHeight = fontRenderer.getStringHeight();
            int xOffset = -textWidth / 2;

            glDisable(GL_TEXTURE_2D);
            glColor4f(0.0F, 0.0F, 0.0F, 0.25F);
            glBegin(GL_QUADS);
            glVertex3f(xOffset - 2, -1, 0);
            glVertex3f(xOffset + textWidth + 2, -1, 0);
            glVertex3f(xOffset + textWidth + 2, textHeight + 1, 0);
            glVertex3f(xOffset - 2, textHeight + 1, 0);
            glEnd();
            glEnable(GL_TEXTURE_2D);

            glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            fontRenderer.drawString(name, xOffset, 0, true);

            Textures.bind(0);

            glPopMatrix();
        }

        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glEnable(GL_FOG);
        glDisable(GL_BLEND);
    }

    @Override
    public void lightColumnChanged(int x, int z, int minY, int maxY) {
        setDirty(x - 1, minY - 1, z - 1, x + 1, maxY + 1, z + 1);
    }

    @Override
    public void tileChanged(int x, int y, int z) {
        setDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
    }

    @Override
    public void allChanged() {
        setDirty(0, 0, 0, this.level.width, this.level.depth, this.level.height);
    }

    public void rebuildAll() {
        int saved = Chunk.rebuiltThisFrame;
        Chunk.rebuiltThisFrame = 0;
        int cap = Integer.MAX_VALUE;

        for (Chunk chunk : this.chunks) {
            chunk.rebuildNow(0);
            chunk.rebuildNow(1);
        }
    }
}