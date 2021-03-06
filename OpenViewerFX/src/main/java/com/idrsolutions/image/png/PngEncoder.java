/*
 * ===========================================
 * Java Pdf Extraction Decoding Access Library
 * ===========================================
 *
 * Project Info:  http://www.idrsolutions.com
 * Help section for developers at http://www.idrsolutions.com/support/
 *
 * (C) Copyright 1997-2015 IDRsolutions and Contributors.
 *
 * This file is part of JPedal/JPDF2HTML5
 *
     This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


 *
 * ---------------
 * PngEncoder.java
 * ---------------
 */
package com.idrsolutions.image.png;

import com.idrsolutions.image.BitWriter;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;

/**
 * Class writes bufferedimages as Fast deflated Png
 * <p>
 * Here is an example of how the code can be used:-
 * </p>
 * <pre><code>
 * PngEncoder encoder = new PngEncoder();
 * encoder.write(image_to_save, outputStream);
 * </code></pre>
 */
public class PngEncoder {

    private boolean compress = false;

    public PngEncoder() {
    }

    /**
     * writes image as PNG in outputstream
     * <p>
     * This method does not close the provided OutputStream after the write
     * operation has completed; it is the responsibility of the caller to close
     * the stream,
     * </p>
     *
     * @param image BufferedImage to write image
     * @param outputStream The stream to write the image to
     * @throws IOException if the image wasn't written
     */
    public void write(final BufferedImage image, final OutputStream outputStream) throws IOException {
        if (compress) {
            compress8Bit(image, outputStream);
        } else {
            compressNormal(image, outputStream);
        }
    }

    /**
     * Returns whether encoder uses 8 bit quantisation compression
     * @return the compression used 
     */
    public boolean isCompressed() {
        return compress;
    }

    /**
     * writes image as 8 bit quantized
     *
     * @param compress compression default is false
     */
    public void setCompressed(boolean compress) {
        this.compress = compress;
    }

    private void compressNormal(final BufferedImage image, final OutputStream outputStream) throws IOException {
        final int bh = image.getHeight();
        final int bw = image.getWidth();
        final ColorModel colorModel = image.getColorModel();
        final boolean hasAlpha = colorModel.hasAlpha();
        final int pLen = colorModel.getPixelSize();
        int nComp = colorModel.getNumComponents();
        final boolean isIndexed = colorModel instanceof IndexColorModel;

        final int bitDepth = calculateBitDepth(pLen, nComp);
        final int colType;

        if (isIndexed) {
            colType = 3;
            nComp = 1;
        } else if (nComp < 3) {
            colType = hasAlpha ? 4 : 0;
        } else if (bitDepth < 8) {
            colType = hasAlpha ? 4 : 0;
        } else {
            colType = hasAlpha ? 6 : 2;
        }

        // write signature
        outputStream.write(PngChunk.SIGNATURE);

        //write header
        PngChunk chunk = PngChunk.createHeaderChunk(bw, bh, (byte) bitDepth, (byte) colType, (byte) 0, (byte) 0, (byte) 0);
        outputStream.write(chunk.getLength());
        outputStream.write(chunk.getName());
        outputStream.write(chunk.getData());
        outputStream.write(chunk.getCRCValue());

        byte[] pixels = getPixelData(image, bitDepth, nComp, bw, bh);
        pixels = getDeflatedData(pixels, Deflater.BEST_SPEED);

        byte[] trnsBytes = null;

        if (isIndexed) { // indexed model need to be created
            final IndexColorModel indexModel = ((IndexColorModel) colorModel);
            final int size = indexModel.getMapSize();
            final byte[] r = new byte[size];
            final byte[] g = new byte[size];
            final byte[] b = new byte[size];
            indexModel.getReds(r);
            indexModel.getGreens(g);
            indexModel.getBlues(b);
            final ByteBuffer bb = ByteBuffer.allocate(size * 3);
            for (int i = 0; i < size; i++) {
                bb.put(new byte[]{r[i], g[i], b[i]});
            }
            chunk = PngChunk.createPaleteChunk(bb.array());
            outputStream.write(chunk.getLength());
            outputStream.write(chunk.getName());
            outputStream.write(chunk.getData());
            outputStream.write(chunk.getCRCValue());

            if (indexModel.getNumComponents() == 4) {
                trnsBytes = new byte[256];
                indexModel.getAlphas(trnsBytes);
            }
        }

        if (trnsBytes != null) {
            chunk = PngChunk.createTrnsChunk(trnsBytes);
            outputStream.write(chunk.getLength());
            outputStream.write(chunk.getName());
            outputStream.write(chunk.getData());
            outputStream.write(chunk.getCRCValue());
        }

        chunk = PngChunk.createDataChunk(pixels);
        outputStream.write(chunk.getLength());
        outputStream.write(chunk.getName());
        outputStream.write(chunk.getData());
        outputStream.write(chunk.getCRCValue());

        // write end
        chunk = PngChunk.createEndChunk();
        outputStream.write(chunk.getLength());
        outputStream.write(chunk.getName());
        outputStream.write(chunk.getData());
        outputStream.write(chunk.getCRCValue());
    }

    private void compress8Bit(final BufferedImage image, final OutputStream outputStream) throws IOException {

        int type = image.getType();
        final int bh = image.getHeight();
        final int bw = image.getWidth();
        final int dim = bh * bw;
        byte[] pixels;
        int[] intPixels;
        byte[] bgrPixels = null;
        byte[] abgrPixels = null;
        byte[] trnsBytes = null;
        int cc = 0;
        int val;

        switch (type) {
            case BufferedImage.TYPE_3BYTE_BGR:
                bgrPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
                break;
            case BufferedImage.TYPE_4BYTE_ABGR:
                abgrPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
                break;
            case BufferedImage.TYPE_INT_BGR:
                bgrPixels = new byte[bh * bw * 3];
                intPixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
                for (int i = 0; i < intPixels.length; i++) {
                    val = intPixels[i];
                    bgrPixels[cc++] = (byte) (val >> 16);
                    bgrPixels[cc++] = (byte) (val >> 8);
                    bgrPixels[cc++] = (byte) val;
                }
                break;
            case BufferedImage.TYPE_INT_ARGB:
                abgrPixels = new byte[bh * bw * 4];
                intPixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
                for (int i = 0; i < intPixels.length; i++) {
                    val = intPixels[i];
                    abgrPixels[cc++] = (byte) (val >> 24);
                    abgrPixels[cc++] = (byte) val;
                    abgrPixels[cc++] = (byte) (val >> 8);
                    abgrPixels[cc++] = (byte) (val >> 16);
                }
                break;
            case BufferedImage.TYPE_INT_RGB:
                bgrPixels = new byte[bh * bw * 3];
                intPixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
                for (int i = 0; i < intPixels.length; i++) {
                    val = intPixels[i];
                    bgrPixels[cc++] = (byte) val;
                    bgrPixels[cc++] = (byte) (val >> 8);
                    bgrPixels[cc++] = (byte) (val >> 16);
                }
                break;
            default:
                compressNormal(image, outputStream);
                return;
        }

        intPixels = null;
        pixels = null;

        final byte[] colorPalette;
        byte[] indexedPixels = new byte[dim + bh];

        if (abgrPixels != null) {
//            adjustAlpha(abgrPixels);
            Quant32 wu = new Quant32();
            Object[] obj = wu.getPalette(abgrPixels);
            wu = null;
            colorPalette = (byte[]) obj[0];
            trnsBytes = (byte[]) obj[1];
            byte[] qBytes = D4.process(colorPalette, trnsBytes, abgrPixels, bh, bw);

            int k = 0;
            int z = 0;
            for (int i = 0; i < bh; i++) {
                indexedPixels[z++] = 0;
                for (int j = 0; j < bw; j++) {
                    indexedPixels[z++] = qBytes[k++];
                }
            }

        } else {

            Quant24 wu = new Quant24();
            colorPalette = wu.getPalette(bgrPixels);
            wu = null;
            byte[] qBytes = D3.process(colorPalette, bgrPixels, bh, bw);

            int k = 0;
            int z = 0;
            for (int i = 0; i < bh; i++) {
                indexedPixels[z++] = 0;
                for (int j = 0; j < bw; j++) {
                    indexedPixels[z++] = qBytes[k++];
                }
            }
        }

        int bitDepth = 8;
        int colType = 3;

        // write signature
        outputStream.write(PngChunk.SIGNATURE);

        //write header
        PngChunk chunk = PngChunk.createHeaderChunk(bw, bh, (byte) bitDepth, (byte) colType, (byte) 0, (byte) 0, (byte) 0);
        outputStream.write(chunk.getLength());
        outputStream.write(chunk.getName());
        outputStream.write(chunk.getData());
        outputStream.write(chunk.getCRCValue());

        pixels = getDeflatedData(indexedPixels, Deflater.BEST_COMPRESSION);

        chunk = PngChunk.createPaleteChunk(colorPalette);
        outputStream.write(chunk.getLength());
        outputStream.write(chunk.getName());
        outputStream.write(chunk.getData());
        outputStream.write(chunk.getCRCValue());

        if (trnsBytes != null) {
            chunk = PngChunk.createTrnsChunk(trnsBytes);
            outputStream.write(chunk.getLength());
            outputStream.write(chunk.getName());
            outputStream.write(chunk.getData());
            outputStream.write(chunk.getCRCValue());
        }

        chunk = PngChunk.createDataChunk(pixels);
        outputStream.write(chunk.getLength());
        outputStream.write(chunk.getName());
        outputStream.write(chunk.getData());
        outputStream.write(chunk.getCRCValue());

        // write end
        chunk = PngChunk.createEndChunk();
        outputStream.write(chunk.getLength());
        outputStream.write(chunk.getName());
        outputStream.write(chunk.getData());
        outputStream.write(chunk.getCRCValue());
    }

    private static byte[] getPixelData(final BufferedImage buff, final int bitDepth, final int nComp, final int bw, final int bh) throws IOException {
        ColorModel model = buff.getColorModel();
        switch (bitDepth) {
            case 1:
            case 2:
            case 4:
                final byte[] pixels = ((DataBufferByte) buff.getRaster().getDataBuffer()).getData();
                final int multi = bitDepth == 1 ? 8 : (bitDepth == 2 ? 4 : 2);
                final PngBitReader reader = new PngBitReader(pixels, true);
                final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                final BitWriter writer = new BitWriter(bos);
                int cc2 = 0; //column count;                
                final int iter = pixels.length * multi;
                for (int i = 0; i < iter; i++) {
                    if (cc2 == 0) {
                        writer.writeByte((byte) 0);
                    }
                    for (int k = 0; k < bitDepth; k++) {
                        writer.writeBit(reader.getPositive(1));
                    }
                    cc2++;
                    if (cc2 == bw) {
                        cc2 = 0;
                    }
                }
                writer.end();
                bos.flush();
                bos.close();
                return bos.toByteArray();
            case 8:
                final DataBuffer dataBuff = buff.getRaster().getDataBuffer();
                final byte[] pixels8;
                switch (dataBuff.getDataType()) {
                    case DataBuffer.TYPE_BYTE:
                        pixels8 = ((DataBufferByte) buff.getRaster().getDataBuffer()).getData();
                        int col = 0;
                        final ByteBuffer bOut = ByteBuffer.allocate(bw * bh * nComp + bh);
                        switch (buff.getType()) {
                            case BufferedImage.TYPE_3BYTE_BGR:
                                for (int p = 0; p < pixels8.length; p += nComp) {
                                    if (col == 0) {
                                        bOut.put((byte) 0);
                                    }
                                    final byte[] b = new byte[]{pixels8[p + 2], pixels8[p + 1], pixels8[p]};
                                    bOut.put(b);
                                    col++;
                                    if (col == bw) {
                                        col = 0;
                                    }
                                }
                                return bOut.array();
                            case BufferedImage.TYPE_4BYTE_ABGR:
                            case BufferedImage.TYPE_4BYTE_ABGR_PRE:
                                for (int p = 0; p < pixels8.length; p += nComp) {
                                    if (col == 0) {
                                        bOut.put((byte) 0);
                                    }
                                    final byte[] b = new byte[]{pixels8[p + 3], pixels8[p + 2], pixels8[p + 1], pixels8[p]};
                                    bOut.put(b);
                                    col++;
                                    if (col == bw) {
                                        col = 0;
                                    }
                                }
                                return bOut.array();
                            default:
                                for (int p = 0; p < pixels8.length; p += nComp) {
                                    if (col == 0) {
                                        bOut.put((byte) 0);
                                    }
                                    for (int i = 0; i < nComp; i++) {
                                        bOut.put(pixels8[p + i]);
                                    }
                                    col++;
                                    if (col == bw) {
                                        col = 0;
                                    }
                                }
                                return bOut.array();

                        }

                    case DataBuffer.TYPE_INT:
                        final int[] pixInt = ((DataBufferInt) buff.getRaster().getDataBuffer()).getData();
                        byte[] output;
                        int k = 0;
                        int p = 0;
                        int val;
                        if (buff.getType() == BufferedImage.TYPE_INT_ARGB || buff.getType() == BufferedImage.TYPE_INT_ARGB_PRE) {
                            output = new byte[bw * bh * 4 + bh];
                            for (int i = 0; i < bh; i++) {
                                output[k++] = 0;
                                for (int j = 0; j < bw; j++) {
                                    val = pixInt[p++];
                                    output[k++] = (byte) (val >> 16);
                                    output[k++] = (byte) (val >> 8);
                                    output[k++] = (byte) val;
                                    output[k++] = (byte) (val >> 24);
                                }
                            }

                        } else if (buff.getType() == BufferedImage.TYPE_INT_RGB) {
                            output = new byte[bw * bh * 3 + bh];
                            for (int i = 0; i < bh; i++) {
                                output[k++] = 0;
                                for (int j = 0; j < bw; j++) {
                                    val = pixInt[p++];
                                    output[k++] = (byte) (val >> 16);
                                    output[k++] = (byte) (val >> 8);
                                    output[k++] = (byte) val;
                                }
                            }

                        } else if (buff.getType() == BufferedImage.TYPE_INT_BGR) {
                            output = new byte[bw * bh * 3 + bh];
                            for (int i = 0; i < bh; i++) {
                                output[k++] = 0;
                                for (int j = 0; j < bw; j++) {
                                    val = pixInt[p++];
                                    output[k++] = (byte) val;
                                    output[k++] = (byte) (val >> 8);
                                    output[k++] = (byte) (val >> 16);
                                }
                            }
                        } else if (model instanceof DirectColorModel) {
                            DirectColorModel dm = (DirectColorModel) model;
                            long rMask = getMaskValue(dm.getRedMask());
                            long gMask = getMaskValue(dm.getGreenMask());
                            long bMask = getMaskValue(dm.getBlueMask());
                            long aMask = getMaskValue(dm.getAlphaMask());

                            output = new byte[bw * bh * 4 + bh];
                            for (int i = 0; i < bh; i++) {
                                output[k++] = 0;
                                for (int j = 0; j < bw; j++) {
                                    val = pixInt[p++];
                                    output[k++] = (byte) (val >> rMask);
                                    output[k++] = (byte) (val >> gMask);
                                    output[k++] = (byte) (val >> bMask);
                                    output[k++] = (byte) (val >> aMask);
                                }
                            }
                        } else {
                            final ByteBuffer out = ByteBuffer.allocate(bw * bh * nComp + bh);
                            int clm = 0;
                            for (final int i : pixInt) {
                                if (clm == 0) {
                                    out.put((byte) 0);
                                }
                                final byte[] t = PngChunk.intToBytes(i);
                                switch (nComp) {
                                    case 4: //argb >> rgbA
                                        out.put(new byte[]{t[1], t[2], t[3], t[0]});
                                        break;
                                    case 3:
                                        out.put(new byte[]{t[1], t[2], t[3]});
                                        break;
                                    case 2:
                                        out.put(new byte[]{t[2], t[3]});
                                        break;
                                    case 1:
                                        out.put(t[3]);
                                        break;
                                }
                                clm++;
                                if (clm == bw) {
                                    clm = 0;
                                }
                            }
                            return out.array();
                        }
                        return output;
                }
            case 16:
                final short[] shortPixels = ((DataBufferUShort) buff.getRaster().getDataBuffer()).getData();
                final ByteBuffer bos16 = ByteBuffer.allocate(shortPixels.length * 2 + bh);
                int scol = 0;
                for (int p = 0; p < shortPixels.length; p += nComp) {
                    if (scol == 0) {
                        bos16.put((byte) 0);
                    }
                    for (int i = 0; i < nComp; i++) {
                        bos16.putShort(shortPixels[p + i]);
                    }
                    scol++;
                    if (scol == bw) {
                        scol = 0;
                    }
                }
                return bos16.array();
        }
        return null;
    }

    private static int getMaskValue(int mask) {
        switch (mask) {
            case 255:
                return 0;
            case 65280:
                return 8;
            case 16711680:
                return 16;
            default:
                return 24;
        }
    }

    private static int calculateBitDepth(final int pixelBits, final int nComp) {
        if (pixelBits < 8) {
            return pixelBits;
        }
        final int c = pixelBits / nComp;
        if (c == 8 || c == 16) {
            return c;
        }
        return 8;
    }

    private static byte[] getDeflatedData(final byte[] pixels, int deflaterType) throws IOException {
        final Deflater deflater = new Deflater(deflaterType);
        deflater.setInput(pixels);
        final int min = Math.min(pixels.length / 2, 4096);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(min);
        deflater.finish();
        final byte[] buffer = new byte[min];
        while (!deflater.finished()) {
            final int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        deflater.end();
        outputStream.close();
        return outputStream.toByteArray();
    }

}
