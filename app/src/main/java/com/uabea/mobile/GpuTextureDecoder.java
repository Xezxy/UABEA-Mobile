package com.uabea.mobile;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Men-decode tekstur terkompresi (ETC1/ETC2, ASTC, DXT/BC, PVRTC) memakai GPU.
 *
 * <p>Cara kerja: buat konteks EGL offscreen, unggah data terkompresi sebagai
 * tekstur, gambar satu quad ke framebuffer RGBA, lalu baca pikselnya. Pendekatan
 * quad dipakai supaya format yang tidak bisa dijadikan lampiran framebuffer
 * (mis. PVRTC) tetap bisa didekode.</p>
 */
public final class GpuTextureDecoder {
    private GpuTextureDecoder() {
    }

    private static final int GL_ETC1_RGB8_OES = 0x8D64;
    private static final int GL_COMPRESSED_RGB8_ETC2 = 0x9274;
    private static final int GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2 = 0x9276;
    private static final int GL_COMPRESSED_RGBA8_ETC2_EAC = 0x9278;
    private static final int GL_COMPRESSED_RGBA_ASTC_4x4 = 0x93B0;
    private static final int GL_COMPRESSED_RGB_S3TC_DXT1_EXT = 0x83F0;
    private static final int GL_COMPRESSED_RGBA_S3TC_DXT3_EXT = 0x83F2;
    private static final int GL_COMPRESSED_RGBA_S3TC_DXT5_EXT = 0x83F3;
    private static final int GL_COMPRESSED_RGBA_BPTC_UNORM = 0x8E8C;
    private static final int GL_COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT = 0x8E8E;
    private static final int GL_COMPRESSED_RED_RGTC1 = 0x8DBB;
    private static final int GL_COMPRESSED_RG_RGTC2 = 0x8DBD;
    private static final int GL_COMPRESSED_RGB_PVRTC_4BPPV1_IMG = 0x8C00;
    private static final int GL_COMPRESSED_RGB_PVRTC_2BPPV1_IMG = 0x8C01;
    private static final int GL_COMPRESSED_RGBA_PVRTC_4BPPV1_IMG = 0x8C02;
    private static final int GL_COMPRESSED_RGBA_PVRTC_2BPPV1_IMG = 0x8C03;

    /** Ukuran blok ASTC 4x4 s/d 12x12 (offset dari GL_COMPRESSED_RGBA_ASTC_4x4). */
    private static final int[] ASTC_OFFSET = {0, 2, 4, 7, 11, 13};

    public static boolean supports(int unityFormat) {
        return glFormat(unityFormat) != 0;
    }

    private static int astcIndex(int f) {
        switch (f) {
            case 48:
            case 54:
                return 0; // 4x4
            case 49:
            case 55:
                return 1; // 5x5
            case 50:
            case 56:
                return 2; // 6x6
            case 51:
            case 57:
                return 3; // 8x8
            case 52:
            case 58:
                return 4; // 10x10
            case 53:
            case 59:
                return 5; // 12x12
            default:
                return -1;
        }
    }

    private static int glFormat(int f) {
        switch (f) {
            case 10:
                return GL_COMPRESSED_RGB_S3TC_DXT1_EXT;
            case 11:
                return GL_COMPRESSED_RGBA_S3TC_DXT3_EXT;
            case 12:
                return GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
            case 24:
                return GL_COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT;
            case 25:
                return GL_COMPRESSED_RGBA_BPTC_UNORM;
            case 26:
                return GL_COMPRESSED_RED_RGTC1;
            case 27:
                return GL_COMPRESSED_RG_RGTC2;
            case 30:
                return GL_COMPRESSED_RGB_PVRTC_2BPPV1_IMG;
            case 31:
                return GL_COMPRESSED_RGBA_PVRTC_2BPPV1_IMG;
            case 32:
                return GL_COMPRESSED_RGB_PVRTC_4BPPV1_IMG;
            case 33:
                return GL_COMPRESSED_RGBA_PVRTC_4BPPV1_IMG;
            case 34:
                return GL_ETC1_RGB8_OES;
            case 45:
                return GL_COMPRESSED_RGB8_ETC2;
            case 46:
                return GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2;
            case 47:
                return GL_COMPRESSED_RGBA8_ETC2_EAC;
            default:
                int astc = astcIndex(f);
                if (astc < 0) {
                    return 0;
                }
                return GL_COMPRESSED_RGBA_ASTC_4x4 + ASTC_OFFSET[astc];
        }
    }

    private static int blockBytes(int f) {
        switch (f) {
            case 30:
            case 31:
            case 32:
            case 33:
                return 0; // PVRTC dihitung per piksel
            case 10:
            case 11:
            case 12:
            case 26:
            case 34:
            case 45:
            case 46:
                return 8;
            case 27:
            case 47:
            case 24:
            case 25:
                return 16;
            default:
                return 16; // ASTC selalu 16 byte per blok
        }
    }

    private static int astcBlockSize(int f) {
        switch (astcIndex(f)) {
            case 0:
                return 4;
            case 1:
                return 5;
            case 2:
                return 6;
            case 3:
                return 8;
            case 4:
                return 10;
            case 5:
                return 12;
            default:
                return 4;
        }
    }

    /** Ukuran data mip 0 sesuai format Unity. */
    private static int level0Size(int f, int w, int h) {
        if (f == 30 || f == 31) { // PVRTC 2bpp
            return Math.max(1, (w * h + 3) / 4);
        }
        if (f == 32 || f == 33) { // PVRTC 4bpp
            return Math.max(1, (w * h + 1) / 2);
        }
        int bs = astcBlockSize(f);
        int bw = (w + bs - 1) / bs;
        int bh = (h + bs - 1) / bs;
        return Math.max(1, bw * bh * blockBytes(f));
    }

    /** Mengembalikan pixel ARGB top-down, atau null bila GPU tidak mendukung format. */
    public static int[] decode(int unityFormat, int width, int height, byte[] data) {
        int format = glFormat(unityFormat);
        if (format == 0 || width <= 0 || height <= 0 || data == null) {
            return null;
        }
        int expected = level0Size(unityFormat, width, height);
        if (data.length < expected) {
            return null;
        }

        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) {
            return null;
        }
        int[] major = new int[1];
        int[] minor = new int[1];
        if (!EGL14.eglInitialize(display, major, 0, minor, 0)) {
            return null;
        }
        EGLConfig config = chooseConfig(display);
        if (config == null) {
            EGL14.eglTerminate(display);
            return null;
        }
        // Coba ES3 dulu (ETC2/ASTC lebih banyak tersedia), lalu ES2.
        EGLContext context = createContext(display, config, 3);
        if (context == null) {
            context = createContext(display, config, 2);
        }
        if (context == null) {
            EGL14.eglTerminate(display);
            return null;
        }
        int[] surfAttribs = {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE};
        EGLSurface surface = EGL14.eglCreatePbufferSurface(display, config, surfAttribs, 0);
        if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroyContext(display, context);
            EGL14.eglTerminate(display);
            return null;
        }
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            cleanup(display, surface, context);
            return null;
        }
        try {
            return drawAndRead(format, width, height, expected, data);
        } catch (Throwable t) {
            return null;
        } finally {
            cleanup(display, surface, context);
        }
    }

    private static EGLContext createContext(EGLDisplay display, EGLConfig config, int version) {
        int[] attribs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, version, EGL14.EGL_NONE};
        EGLContext c = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, attribs, 0);
        return (c == null || c == EGL14.EGL_NO_CONTEXT) ? null : c;
    }

    private static int[] drawAndRead(int format, int width, int height, int expected, byte[] data) {
        int[] tex = new int[1];
        int[] fbo = new int[1];
        int[] outTex = new int[1];
        int program = 0;
        try {
            GLES20.glGenTextures(1, tex, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            ByteBuffer src = ByteBuffer.allocateDirect(expected);
            src.put(data, 0, expected);
            src.position(0);
            while (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
                // bersihkan error lama
            }
            GLES20.glCompressedTexImage2D(GLES20.GL_TEXTURE_2D, 0, format, width, height, 0, expected, src);
            if (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
                return null;
            }

            // Tekstur tujuan (RGBA8) sebagai lampiran framebuffer.
            GLES20.glGenTextures(1, outTex, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, outTex[0]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES20.glGenFramebuffers(1, fbo, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, outTex[0], 0);
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                return null;
            }

            program = buildProgram();
            if (program == 0) {
                return null;
            }
            GLES20.glUseProgram(program);
            GLES20.glViewport(0, 0, width, height);
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_CULL_FACE);

            FloatBuffer quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            quad.put(new float[]{-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f});
            quad.position(0);
            int aPos = GLES20.glGetAttribLocation(program, "aPos");
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(aPos);

            ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
            GLES20.glPixelStorei(GLES20.GL_PACK_ALIGNMENT, 1);
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);
            if (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
                return null;
            }
            int[] out = new int[width * height];
            for (int y = 0; y < height; y++) {
                // glReadPixels baris 0 = bawah; keluaran kita baris 0 = atas.
                int srcRow = (height - 1 - y) * width * 4;
                for (int x = 0; x < width; x++) {
                    int p = srcRow + x * 4;
                    int r = pixels.get(p) & 0xFF;
                    int g = pixels.get(p + 1) & 0xFF;
                    int b = pixels.get(p + 2) & 0xFF;
                    int a = pixels.get(p + 3) & 0xFF;
                    out[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
            return out;
        } finally {
            try {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                if (fbo[0] != 0) {
                    GLES20.glDeleteFramebuffers(1, fbo, 0);
                }
                if (tex[0] != 0) {
                    GLES20.glDeleteTextures(1, tex, 0);
                }
                if (outTex[0] != 0) {
                    GLES20.glDeleteTextures(1, outTex, 0);
                }
                if (program != 0) {
                    GLES20.glDeleteProgram(program);
                }
            } catch (Throwable ignored) {
                // abaikan
            }
        }
    }

    private static int buildProgram() {
        String vs = "attribute vec2 aPos;\n"
                + "varying vec2 vUv;\n"
                + "void main() {\n"
                + "  vUv = aPos * 0.5 + 0.5;\n"
                + "  gl_Position = vec4(aPos, 0.0, 1.0);\n"
                + "}\n";
        String fs = "precision mediump float;\n"
                + "uniform sampler2D uTex;\n"
                + "varying vec2 vUv;\n"
                + "void main() {\n"
                + "  gl_FragColor = texture2D(uTex, vUv);\n"
                + "}\n";
        int v = compile(GLES20.GL_VERTEX_SHADER, vs);
        if (v == 0) {
            return 0;
        }
        int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        if (f == 0) {
            GLES20.glDeleteShader(v);
            return 0;
        }
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] status = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0);
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        if (status[0] == 0) {
            GLES20.glDeleteProgram(p);
            return 0;
        }
        return p;
    }

    private static int compile(int type, String source) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, source);
        GLES20.glCompileShader(s);
        int[] status = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            GLES20.glDeleteShader(s);
            return 0;
        }
        return s;
    }

    private static EGLConfig chooseConfig(EGLDisplay display) {
        int[] attribs = {
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) {
            return null;
        }
        return configs[0];
    }

    private static void cleanup(EGLDisplay display, EGLSurface surface, EGLContext context) {
        try {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(display, surface);
            EGL14.eglDestroyContext(display, context);
            EGL14.eglTerminate(display);
        } catch (Throwable ignored) {
            // abaikan
        }
    }
}
