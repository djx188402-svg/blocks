package net.mcreator.blocks.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class DdsImage {
	private static final int DDS_MAGIC = 0x20534444;
	private static final int DDPF_FOURCC = 0x4;
	private static final int DDPF_RGB = 0x40;
	private static final int DDPF_ALPHAPIXELS = 0x1;

	private DdsImage() {
	}

	static DecodedDds decode(InputStream inputStream) throws IOException {
		byte[] bytes = toByteArray(inputStream);
		if (bytes.length < 128) {
			throw new IOException("DDS file is too small");
		}

		ByteBuffer header = ByteBuffer.wrap(bytes, 0, 128).order(ByteOrder.LITTLE_ENDIAN);
		if (header.getInt(0) != DDS_MAGIC) {
			throw new IOException("Not a DDS file");
		}

		int height = header.getInt(12);
		int width = header.getInt(16);
		int mipMapCount = Math.max(1, header.getInt(28));
		int pfFlags = header.getInt(80);
		int fourCC = header.getInt(84);
		int rgbBitCount = header.getInt(88);
		int rMask = header.getInt(92);
		int gMask = header.getInt(96);
		int bMask = header.getInt(100);
		int aMask = header.getInt(104);

		if (width <= 0 || height <= 0) {
			throw new IOException("Invalid DDS dimensions");
		}

		int[][] mipmaps = new int[mipMapCount][];
		int offset = 128;
		int mipWidth = width;
		int mipHeight = height;

		for (int mip = 0; mip < mipMapCount; mip++) {
			if ((pfFlags & DDPF_FOURCC) != 0) {
				if (fourCC == fourCC("DXT1")) {
					int blockSize = 8;
					int dataSize = ((mipWidth + 3) / 4) * ((mipHeight + 3) / 4) * blockSize;
					ensureRange(bytes, offset, dataSize);
					mipmaps[mip] = decodeDxt1(bytes, offset, mipWidth, mipHeight);
					offset += dataSize;
				} else if (fourCC == fourCC("DXT3")) {
					int blockSize = 16;
					int dataSize = ((mipWidth + 3) / 4) * ((mipHeight + 3) / 4) * blockSize;
					ensureRange(bytes, offset, dataSize);
					mipmaps[mip] = decodeDxt3(bytes, offset, mipWidth, mipHeight);
					offset += dataSize;
				} else if (fourCC == fourCC("DXT5")) {
					int blockSize = 16;
					int dataSize = ((mipWidth + 3) / 4) * ((mipHeight + 3) / 4) * blockSize;
					ensureRange(bytes, offset, dataSize);
					mipmaps[mip] = decodeDxt5(bytes, offset, mipWidth, mipHeight);
					offset += dataSize;
				} else {
					throw new IOException("Unsupported DDS compression: " + intToFourCC(fourCC));
				}
			} else if ((pfFlags & DDPF_RGB) != 0 && rgbBitCount == 32) {
				int dataSize = mipWidth * mipHeight * 4;
				ensureRange(bytes, offset, dataSize);
				mipmaps[mip] = decodeRgba(bytes, offset, mipWidth, mipHeight, rMask, gMask, bMask,
						(pfFlags & DDPF_ALPHAPIXELS) != 0 ? aMask : 0);
				offset += dataSize;
			} else {
				throw new IOException("Unsupported DDS pixel format");
			}

			mipWidth = Math.max(1, mipWidth >> 1);
			mipHeight = Math.max(1, mipHeight >> 1);
		}

		return new DecodedDds(width, height, mipmaps);
	}

	private static int[] decodeRgba(byte[] bytes, int offset, int width, int height, int rMask, int gMask, int bMask, int aMask) {
		int[] pixels = new int[width * height];
		for (int i = 0; i < pixels.length; i++) {
			int p = (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8) | ((bytes[offset + 2] & 0xFF) << 16)
					| ((bytes[offset + 3] & 0xFF) << 24);
			offset += 4;
			int r = extractChannel(p, rMask);
			int g = extractChannel(p, gMask);
			int b = extractChannel(p, bMask);
			int a = aMask == 0 ? 255 : extractChannel(p, aMask);
			pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
		}
		return pixels;
	}

	private static int extractChannel(int value, int mask) {
		if (mask == 0) {
			return 0;
		}
		int shift = Integer.numberOfTrailingZeros(mask);
		int max = mask >>> shift;
		int raw = (value & mask) >>> shift;
		if (max == 0) {
			return 0;
		}
		return (raw * 255) / max;
	}

	private static int[] decodeDxt1(byte[] bytes, int offset, int width, int height) {
		int[] pixels = new int[width * height];
		int blocksWide = (width + 3) / 4;
		int blocksHigh = (height + 3) / 4;

		for (int by = 0; by < blocksHigh; by++) {
			for (int bx = 0; bx < blocksWide; bx++) {
				int c0 = readU16(bytes, offset);
				int c1 = readU16(bytes, offset + 2);
				int codes = readU32(bytes, offset + 4);
				offset += 8;

				int[] palette = decodeColorPaletteDxt1(c0, c1);
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int code = (codes >>> (2 * (4 * py + px))) & 0x3;
						int x = bx * 4 + px;
						int y = by * 4 + py;
						if (x < width && y < height) {
							pixels[y * width + x] = palette[code];
						}
					}
				}
			}
		}
		return pixels;
	}


	private static int[] decodeDxt3(byte[] bytes, int offset, int width, int height) {
		int[] pixels = new int[width * height];
		int blocksWide = (width + 3) / 4;
		int blocksHigh = (height + 3) / 4;

		for (int by = 0; by < blocksHigh; by++) {
			for (int bx = 0; bx < blocksWide; bx++) {
				long alphaBits = 0;
				for (int i = 0; i < 8; i++) {
					alphaBits |= (long) (bytes[offset + i] & 0xFF) << (8 * i);
				}
				int c0 = readU16(bytes, offset + 8);
				int c1 = readU16(bytes, offset + 10);
				int codes = readU32(bytes, offset + 12);
				offset += 16;

				int[] colorPalette = decodeColorPaletteDxt3Or5(c0, c1);
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int pixelIndex = 4 * py + px;
						int alpha = (int) ((alphaBits >>> (4 * pixelIndex)) & 0xF);
						alpha = (alpha << 4) | alpha;
						int colorCode = (codes >>> (2 * pixelIndex)) & 0x3;
						int rgb = colorPalette[colorCode] & 0x00FFFFFF;
						int x = bx * 4 + px;
						int y = by * 4 + py;
						if (x < width && y < height) {
							pixels[y * width + x] = (alpha << 24) | rgb;
						}
					}
				}
			}
		}
		return pixels;
	}

	private static int[] decodeDxt5(byte[] bytes, int offset, int width, int height) {
		int[] pixels = new int[width * height];
		int blocksWide = (width + 3) / 4;
		int blocksHigh = (height + 3) / 4;

		for (int by = 0; by < blocksHigh; by++) {
			for (int bx = 0; bx < blocksWide; bx++) {
				int a0 = bytes[offset] & 0xFF;
				int a1 = bytes[offset + 1] & 0xFF;
				long alphaBits = 0;
				for (int i = 0; i < 6; i++) {
					alphaBits |= (long) (bytes[offset + 2 + i] & 0xFF) << (8 * i);
				}
				int c0 = readU16(bytes, offset + 8);
				int c1 = readU16(bytes, offset + 10);
				int codes = readU32(bytes, offset + 12);
				offset += 16;

				int[] alphaPalette = decodeAlphaPaletteDxt5(a0, a1);
				int[] colorPalette = decodeColorPaletteDxt3Or5(c0, c1);
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int pixelIndex = 4 * py + px;
						int alphaCode = (int) ((alphaBits >>> (3 * pixelIndex)) & 0x7);
						int alpha = alphaPalette[alphaCode];
						int colorCode = (codes >>> (2 * pixelIndex)) & 0x3;
						int rgb = colorPalette[colorCode] & 0x00FFFFFF;
						int x = bx * 4 + px;
						int y = by * 4 + py;
						if (x < width && y < height) {
							pixels[y * width + x] = (alpha << 24) | rgb;
						}
					}
				}
			}
		}
		return pixels;
	}

	private static int[] decodeColorPaletteDxt1(int c0, int c1) {
		int[] palette = new int[4];
		palette[0] = rgb565ToArgb(c0, 255);
		palette[1] = rgb565ToArgb(c1, 255);
		if (c0 > c1) {
			palette[2] = interpolate(palette[0], palette[1], 2, 1, 3, 255);
			palette[3] = interpolate(palette[0], palette[1], 1, 2, 3, 255);
		} else {
			palette[2] = interpolate(palette[0], palette[1], 1, 1, 2, 255);
			palette[3] = 0;
		}
		return palette;
	}

	private static int[] decodeColorPaletteDxt3Or5(int c0, int c1) {
		int[] palette = new int[4];
		palette[0] = rgb565ToArgb(c0, 255);
		palette[1] = rgb565ToArgb(c1, 255);
		palette[2] = interpolate(palette[0], palette[1], 2, 1, 3, 255);
		palette[3] = interpolate(palette[0], palette[1], 1, 2, 3, 255);
		return palette;
	}

	private static int[] decodeAlphaPaletteDxt5(int a0, int a1) {
		int[] palette = new int[8];
		palette[0] = a0;
		palette[1] = a1;
		if (a0 > a1) {
			for (int i = 2; i < 8; i++) {
				palette[i] = ((8 - i) * a0 + (i - 1) * a1) / 7;
			}
		} else {
			for (int i = 2; i < 6; i++) {
				palette[i] = ((6 - i) * a0 + (i - 1) * a1) / 5;
			}
			palette[6] = 0;
			palette[7] = 255;
		}
		return palette;
	}

	private static int interpolate(int c0, int c1, int w0, int w1, int div, int a) {
		int r = (((c0 >> 16) & 0xFF) * w0 + ((c1 >> 16) & 0xFF) * w1) / div;
		int g = (((c0 >> 8) & 0xFF) * w0 + ((c1 >> 8) & 0xFF) * w1) / div;
		int b = ((c0 & 0xFF) * w0 + (c1 & 0xFF) * w1) / div;
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int rgb565ToArgb(int value, int alpha) {
		int r = (value >> 11) & 0x1F;
		int g = (value >> 5) & 0x3F;
		int b = value & 0x1F;
		r = (r << 3) | (r >> 2);
		g = (g << 2) | (g >> 4);
		b = (b << 3) | (b >> 2);
		return (alpha << 24) | (r << 16) | (g << 8) | b;
	}

	private static int readU16(byte[] data, int off) {
		return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8);
	}

	private static int readU32(byte[] data, int off) {
		return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8) | ((data[off + 2] & 0xFF) << 16) | ((data[off + 3] & 0xFF) << 24);
	}

	private static void ensureRange(byte[] data, int offset, int size) throws IOException {
		if (offset < 0 || size < 0 || offset + size > data.length) {
			throw new IOException("Unexpected end of DDS data");
		}
	}

	private static int fourCC(String name) {
		return name.charAt(0) | (name.charAt(1) << 8) | (name.charAt(2) << 16) | (name.charAt(3) << 24);
	}

	private static String intToFourCC(int fourCC) {
		return "" + (char) (fourCC & 0xFF) + (char) ((fourCC >> 8) & 0xFF) + (char) ((fourCC >> 16) & 0xFF)
				+ (char) ((fourCC >> 24) & 0xFF);
	}

	private static byte[] toByteArray(InputStream inputStream) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = inputStream.read(buffer)) != -1) {
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	static final class DecodedDds {
		final int width;
		final int height;
		final int[][] mipmaps;

		DecodedDds(int width, int height, int[][] mipmaps) {
			this.width = width;
			this.height = height;
			this.mipmaps = mipmaps;
		}
	}
}
