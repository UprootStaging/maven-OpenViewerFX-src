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
 * TiffEncoder.java
 * ---------------
 */
package com.idrsolutions.image.tiff;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * Class writes bufferedimages as Tiff 
 * <p>
 * Here is an example of how the code can be used.:-
 * </p>
 * 
 * 
 * <pre><code>
 * TiffEncoder encoder = new TiffEncoder();
 * encoder.setCompress(true); //default is false
 * encoder.write(image_to_save,bos);
 * </code></pre>
 * 
 * Code below exemplifies how to write several images to single output file
 *
 * <pre><code>
 * TiffEncoder encoder = new TiffEncoder();
 * for(BufferedImage image: yourImageArray){
 *     encoder.append(image,filename); 
 * }
 * </code></pre>
 * 
 * 
 *
 */
public class TiffEncoder {

    private boolean compress = false;

    public TiffEncoder() {
    }

    /**
     * writes image as Tiff in outputstream
     * <p>
     * This method does not close the provided OutputStream after the write
     * operation has completed; it is the responsibility of the caller to close
     * the stream,
     * </p>
     *
     * @param image BufferedImage to write image
     * @param outputStream The stream to write to
     * @throws IOException if the image wasn't written
     */
    public void write(BufferedImage image, OutputStream outputStream) throws IOException {
        byte[] raw = getComponentBytes(image);
        int imageH = image.getHeight();
        int imageW = image.getWidth();

        boolean hasAlpha = raw.length > (imageH * imageW * 3);
        if (compress) {
            raw = performCompression(raw, imageW, imageH, hasAlpha);
        }

        int offsetIFD = 8;
        writeIdentifier(outputStream, offsetIFD);
        writeContents(outputStream, raw, imageW, imageH, offsetIFD, hasAlpha, compress);
        writePadding(outputStream, raw.length);
    }

    /**
     * appends image to given tiff file
     * <p>
     * if file is empty then this method write image to the file otherwise it
     * will append the image after previous image.
     * </p>
     * <strong>Please note: This method supports only Big endian tiff
     * files</strong>
     *
     * @param img BufferedImage to append image
     * @param fileName The name of the file where the image will be written 
     * @throws IOException if the file is unreadable 
     */
    public void append(BufferedImage img, String fileName) throws IOException {
        File file = new File(fileName);
        if (file.exists() && file.length() > 0) {
            int endFile = (int) file.length();
            int padding = endFile % 8;
            RandomAccessFile rFile = new RandomAccessFile(fileName, "rw");
            rFile.seek(endFile);
            for (int i = 0; i < padding; i++) {
                rFile.write(0);
            }
            endFile += padding;
            alterLastIFDOffset(rFile, endFile);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] data = getComponentBytes(img);
            boolean hasAlpha = data.length > (img.getWidth() * img.getHeight() * 3);
            if (compress) {
                data = performCompression(data, img.getWidth(), img.getHeight(), hasAlpha);
            }
            writeContents(bos, data, img.getWidth(), img.getHeight(), endFile, hasAlpha, compress);
            bos.close();
            data = bos.toByteArray();
            rFile.seek(endFile);
            rFile.write(data);
            rFile.close();

        } else {
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(fileName));
            createImage(img, out, compress);
            out.close();
        }
    }

    /**
     * @ return whether pack bit compression
     *
     */
    public boolean isCompressed() {
        return compress;
    }

    /**
     * set to use pack bit compression in tiff files
     *
     * @param compress A boolean value pack bit compression in tiff files
     */
    public void setCompressed(boolean compress) {
        this.compress = compress;
    }

    private static void alterLastIFDOffset(RandomAccessFile rFile, int fileLen) throws IOException {
        rFile.seek(0);
        rFile.skipBytes(4);
        int offsetIFD = rFile.readInt();
        while (true) {
            rFile.seek(offsetIFD);
            int dirs = rFile.readShort();
            rFile.skipBytes(dirs * 12);
            offsetIFD = rFile.readInt();
            if (offsetIFD == 0) {
                int pointer = (int) rFile.getFilePointer();
                rFile.seek(pointer - 4);
                rFile.writeInt(fileLen);
                break;
            }
        }
    }

    /**
     * static method to take a standard BufferedImage and write out as Tif to
     * file defined in OutputStream with option to compress the file.
     *
     * @param image BufferedImage to write image
     * @param out The stream to write to
     * @param isCompress A boolean value to compress file
     * @throws IOException if the file cannot be created
     */
    private static void createImage(BufferedImage image, OutputStream out, boolean isCompress) throws IOException {
        byte[] raw = getComponentBytes(image);
        int imageH = image.getHeight();
        int imageW = image.getWidth();

        boolean hasAlpha = raw.length > (imageH * imageW * 3);
        if (isCompress) {
            raw = performCompression(raw, imageW, imageH, hasAlpha);
        }

        int offsetIFD = 8;
        writeIdentifier(out, offsetIFD);
        writeContents(out, raw, imageW, imageH, offsetIFD, hasAlpha, isCompress);
        writePadding(out, raw.length);
    }

    private static byte[] getComponentBytes(BufferedImage image) {
        int imageH = image.getHeight();
        int imageW = image.getWidth();

        byte[] raw;
        byte[] pByte;
        int[] pixInts;
        int index = 0;
//

        switch (image.getType()) {
            case BufferedImage.TYPE_INT_RGB:
                raw = new byte[imageH * imageW * 3];
                pixInts = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
                int aa = 0;

                for (int i = 0; i < imageH; i++) {
                    for (int j = 0; j < imageW; j++) {
                        byte[] rgb = intToBytes(pixInts[aa]);
                        raw[index++] = rgb[1];
                        raw[index++] = rgb[2];
                        raw[index++] = rgb[3];
                        aa++;
                    }
                }
                break;
            case BufferedImage.TYPE_INT_ARGB:
                raw = new byte[imageH * imageW * 4];
                pixInts = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
                int xx = 0;
                for (int i = 0; i < imageH; i++) {
                    for (int j = 0; j < imageW; j++) {
                        byte[] argb = intToBytes(pixInts[xx]);
                        raw[index++] = argb[1];
                        raw[index++] = argb[2];
                        raw[index++] = argb[3];
                        raw[index++] = argb[0];
                        xx++;
                    }
                }
                break;
            case BufferedImage.TYPE_INT_BGR:
                raw = new byte[imageH * imageW * 3];
                pixInts = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
                int bb = 0;
                for (int i = 0; i < imageH; i++) {
                    for (int j = 0; j < imageW; j++) {
                        byte[] bgr = intToBytes(pixInts[bb]);
                        raw[index++] = bgr[3];
                        raw[index++] = bgr[2];
                        raw[index++] = bgr[1];
                        bb++;
                    }
                }
                break;
            case BufferedImage.TYPE_3BYTE_BGR:
                raw = new byte[imageH * imageW * 3];
                pByte = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
                int pb = 0;
                for (int i = 0; i < imageH; i++) {
                    for (int j = 0; j < imageW; j++) {
                        raw[index++] = pByte[pb + 2];
                        raw[index++] = pByte[pb + 1];
                        raw[index++] = pByte[pb];
                        pb += 3;
                    }
                }
                break;
            case BufferedImage.TYPE_4BYTE_ABGR:
                raw = new byte[imageH * imageW * 4];
                pByte = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
                int pb4 = 0;
                for (int i = 0; i < imageH; i++) {
                    for (int j = 0; j < imageW; j++) {
                        raw[index++] = pByte[pb4 + 3];
                        raw[index++] = pByte[pb4 + 2];
                        raw[index++] = pByte[pb4 + 1];
                        raw[index++] = pByte[pb4];
                        pb4 += 4;
                    }
                }
                break;
            default:
                raw = new byte[imageH * imageW * 3];
                BufferedImage bImage = new BufferedImage(imageW, imageH, BufferedImage.TYPE_INT_RGB);
                bImage.createGraphics().drawImage(image, 0, 0, null);
                pixInts = ((DataBufferInt) bImage.getRaster().getDataBuffer()).getData();
                int cc = 0;
                for (int i = 0; i < imageH; i++) {
                    for (int j = 0; j < imageW; j++) {
                        byte[] rgb = intToBytes(pixInts[cc]);
                        raw[index++] = rgb[1];
                        raw[index++] = rgb[2];
                        raw[index++] = rgb[3];
                        cc++;
                    }
                }
                break;
        }
        return raw;
    }

    private static void writeIdentifier(OutputStream out, int offsetIFD) throws IOException {
        out.write(new byte[]{77, 77});// MM big endian
        out.write(new byte[]{0, 42}); // 42 magic number
        out.write(intToBytes(offsetIFD)); // offset to IFD  
    }

    private static int writePadding(OutputStream out, int rawLen) throws IOException {
        int balance = rawLen % 8;
        for (int i = 0; i < balance; i++) {
            out.write(0);
        }
        return balance;
    }

    private static void writeContents(OutputStream out, byte[] raw, int imageWidth, int imageHeight, int offsetIFD, boolean hasAlpha, boolean isCompress) throws IOException {

        int nComp = hasAlpha ? 4 : 3;

        byte totalEntries = (byte) (hasAlpha ? 13 : 12);
        int dataLen = raw.length;

        int sampleOffset = offsetIFD + 2 + (totalEntries * 12) + 4;

        int offsetXR = sampleOffset + (2 * nComp);
        int offsetYR = offsetXR + 8;
        int offsetStrip = offsetYR + 8;

        out.write(shortToBytes(totalEntries));

        //image width
        out.write(shortToBytes((short) 256));
        out.write(shortToBytes((short) 3));
        out.write(intToBytes(1));
        out.write(shortToBytes((short) imageWidth));
        out.write(shortToBytes((short) 0));

        //image height
        out.write(shortToBytes((short) 257));
        out.write(shortToBytes((short) 3));
        out.write(intToBytes(1));
        out.write(shortToBytes((short) imageHeight));
        out.write(shortToBytes((short) 0));

        //bits per sample
        out.write(shortToBytes((short) 258));
        out.write(shortToBytes((short) 3));
        out.write(intToBytes(nComp));
        out.write(intToBytes(sampleOffset));

        //compression
        out.write(shortToBytes((short) 259));
        out.write(shortToBytes((short) 3));
        out.write(intToBytes(1));
        int compressValue = isCompress ? 32773 : 1;
        out.write(shortToBytes((short) compressValue));
        out.write(shortToBytes((short) 0));

        //PhotometricInterpretation 
        out.write(shortToBytes((short) 262));
        out.write(shortToBytes((short) 3));
        out.write(intToBytes(1));
        out.write(shortToBytes((short) 2));
        out.write(shortToBytes((short) 0));

        //StripOffset
        out.write(shortToBytes((short) 273));
        out.write(shortToBytes((short) 4));
        out.write(intToBytes(1));
        out.write(intToBytes(offsetStrip));

        //SamplesPerPixel
        out.write(shortToBytes((short) 277));
        out.write(shortToBytes((short) 3));
        out.write(intToBytes(1));
        out.write(shortToBytes((short) nComp));
        out.write(shortToBytes((short) 0));

        //RowsPerStripe
        out.write(shortToBytes((short) 278));
        out.write(shortToBytes((short) 4));
        out.write(intToBytes(1));
        out.write(intToBytes(imageHeight));

        //StripByteCounts
        out.write(shortToBytes((short) 279));
        out.write(shortToBytes((short) 4));
        out.write(intToBytes(1));
        out.write(intToBytes(dataLen));

        //xresolution
        out.write(shortToBytes((short) 282));
        out.write(shortToBytes((short) 5));
        out.write(intToBytes(1));
        out.write(intToBytes(offsetXR));

        //yresolution
        out.write(shortToBytes((short) 283));
        out.write(shortToBytes((short) 5));
        out.write(intToBytes(1));
        out.write(intToBytes(offsetYR));

        //Resolution unit
        out.write(shortToBytes((short) 296));
        out.write(shortToBytes((short) 3));
        out.write(intToBytes(1));
        out.write(shortToBytes((short) 1));
        out.write(shortToBytes((short) 0));

        if (hasAlpha) {
            out.write(shortToBytes((short) 338));
            out.write(shortToBytes((short) 3));
            out.write(intToBytes(1));
            out.write(shortToBytes((short) 2));
            out.write(shortToBytes((short) 0));
        }

        out.write(intToBytes(0));

        for (int i = 0; i < nComp; i++) {
            out.write(shortToBytes((short) 8));
        }

        out.write(intToBytes(1));
        out.write(intToBytes(1));

        out.write(intToBytes(1));
        out.write(intToBytes(1));

        out.write(raw);
    }

    private static byte[] shortToBytes(short sValue) {
        int iVal = sValue & 0xffff;
        return new byte[]{(byte) (iVal >> 8), (byte) iVal};
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
    }

    private static byte[] performCompression(byte[] data, int imageW, int imageH, boolean hasAlpha) throws IOException {
        int rowByteCount = hasAlpha ? imageW * 4 : imageW * 3;
        int offset = 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (int i = 0; i < imageH; i++) {
            byte temp[] = new byte[rowByteCount];
            System.arraycopy(data, offset, temp, 0, rowByteCount);
            offset += rowByteCount;
            bos.write(compressRow(temp));
        }
        bos.close();
        return bos.toByteArray();
    }

    private static int getNextDup(byte bytes[], int offset) {
        if (offset >= bytes.length) {
            return -1;
        }
        byte temp = bytes[offset];

        for (int i = offset + 1; i < bytes.length; i++) {
            byte b = bytes[i];

            if (b == temp) {
                return i - 1;
            }

            temp = b;
        }

        return -1;
    }

    private static int getNextRun(byte bytes[], int offset) {
        byte b = bytes[offset];
        int c = 0;
        int len = bytes.length;
        for (int i = offset + 1; (i < len) && (bytes[i] == b); i++) {
            c = i;
        }

        return c - offset;
    }

    private static byte[] compressRow(byte bytes[]) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        int pos = 0;
        while (pos < bytes.length) {
            int dup = getNextDup(bytes, pos);
            if (dup == pos) {
                int len = getNextRun(bytes, dup);
                int dataLen = Math.min(len, 128);
                bos.write(-(dataLen - 1));
                bos.write(bytes[pos]);
                pos += dataLen;
            } else {
                int len = dup - pos;

                if (dup > 0) {
                    int runlen = getNextRun(bytes, dup);
                    if (runlen < 3) {
                        int nextptr = pos + len + runlen;
                        int nextdup = getNextDup(bytes, nextptr);
                        if (nextdup != nextptr) {
                            dup = nextdup;
                            len = dup - pos;
                        }
                    }
                }

                if (dup < 0) {
                    len = bytes.length - pos;
                }
                int dataLen = Math.min(len, 128);
                bos.write(dataLen - 1);
                for (int i = 0; i < dataLen; i++) {
                    bos.write(bytes[pos]);
                    pos++;
                }
            }
        }
        bos.close();
        return bos.toByteArray();
    }

}
