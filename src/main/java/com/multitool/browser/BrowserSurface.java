package com.multitool.browser;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;

/**
 * Показывает кадр MCEF в GUI: текстура браузера принадлежит MCEF, здесь она только подставляется
 * в зарегистрированную в TextureManager текстуру-обёртку и рисуется обычным drawTexture.
 * Это единственное место, зависящее от внутреннего устройства AbstractTexture в 1.21.11.
 */
final class BrowserSurface {
    /** Обёртка: владелец GPU-текстуры — MCEF, поэтому close() ничего не освобождает. */
    private static final class Wrapper extends AbstractTexture {
        void bind(GpuTexture texture, GpuTextureView view) {
            this.glTexture = texture;
            this.glTextureView = view;
            if (this.sampler == null) {
                // в 1.21.11 у текстуры есть свой сэмплер; берём готовый у атласа блоков (setFilter убрали)
                AbstractTexture ref = MinecraftClient.getInstance().getTextureManager()
                        .getTexture(Identifier.ofVanilla("textures/atlas/blocks.png"));
                this.sampler = ref.getSampler();
            }
        }

        @Override
        public void close() {
        }
    }

    private final Identifier id;
    private final Wrapper wrapper = new Wrapper();
    private GpuTextureView bound;
    private boolean registered;

    BrowserSurface(Identifier id) {
        this.id = id;
    }

    /** true, если у браузера уже есть готовый кадр и он нарисован. */
    boolean draw(DrawContext c, MCEFBrowser browser, int x, int y, int w, int h) {
        GpuTextureView view = browser.getTextureView();
        GpuTexture texture = browser.getTexture();
        if (view == null || texture == null) {
            return false;
        }
        if (view != bound) {
            wrapper.bind(texture, view);
            bound = view;
        }
        if (!registered) {
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, wrapper);
            registered = true;
        }
        c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, w, h);
        return true;
    }

    void release() {
        if (registered) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
            registered = false;
        }
        bound = null;
    }
}
