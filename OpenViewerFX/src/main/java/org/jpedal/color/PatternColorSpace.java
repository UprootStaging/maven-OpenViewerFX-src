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
 * PatternColorSpace.java
 * ---------------
 */
package org.jpedal.color;

import com.idrsolutions.pdf.color.shading.ShadedPaint;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.shape.Path;
import org.jpedal.exception.PdfException;
import org.jpedal.io.ObjectStore;
import org.jpedal.io.PdfObjectReader;
import org.jpedal.objects.GraphicsState;
import org.jpedal.objects.raw.*;
import org.jpedal.parser.PdfStreamDecoderForPattern;
import org.jpedal.parser.ValueTypes;
import org.jpedal.render.*;
import org.jpedal.utils.LogWriter;


/**
 * handle Pattern ColorSpace (there is also a shading class)
 */
public class PatternColorSpace extends GenericColorSpace{
    
    boolean newFlag;
    
    //lookup tables for stored previous values
    private final Map cachedPaints=new HashMap();
    
    //local copy so we can access File data
    private final PdfObjectReader currentPdfFile;
    
    private boolean colorsReversed;
    
    /**new pattern code image*/
    BufferedImage patternImage;
    
    PatternObject PatternObj;
    
    final GenericColorSpace patternColorSpace;
    
    float[][] matrix;
    
    PdfPaint strokCol;
    
    // Store the pattern cell for JavaFX
    private BufferedImage fullImage;
    /**
     * Just initialises variables
     * @param currentPdfFile
     */
    public PatternColorSpace(final PdfObjectReader currentPdfFile, final GenericColorSpace patternColorSpace){
        
        setType(ColorSpaces.Pattern);
        
        this.currentPdfFile = currentPdfFile;
        this.patternColorSpace=patternColorSpace;
        
        //default value for color
        currentColor = new PdfColor(1.0f,1.0f,1.0f);
    }
    
    /**
     * convert color value to pattern
     */
    @Override
    public void setColor(final String[] value_loc, final int operandCount){
        
        if(patternColorSpace!=null){
            
            final int elementCount=value_loc.length-1;
            final String[] colVals=new String[elementCount];
            for(int i=0;i<elementCount;i++) {
                colVals[i]=value_loc[elementCount-i];
            }
            
            patternColorSpace.setColor(colVals, elementCount);
            strokCol=patternColorSpace.getColor();
        }
        
        PatternObj=(PatternObject) patterns.get(value_loc[0]);
        
//        if(PatternObj.getObjectRefAsString().equals("614 0 R")){
//            currentColor=new PdfColor(0,255,0);
//            return ;
//        }
        
        
        //if already setup just reuse
        final String ref=PatternObj.getObjectRefAsString();
        if(ref!=null && cachedPaints.containsKey(ref)){
            currentColor = (PdfPaint) cachedPaints.get(ref);
            
            return;
        }
        
        
        /**
         * decode Pattern on first use
         */
        
        //ensure read
        currentPdfFile.checkResolved(PatternObj);
        
        //lookup table
        final byte[] streamData=currentPdfFile.readStream(PatternObj,true,true,true, false,false, PatternObj.getCacheName(currentPdfFile.getObjectReader()));
        
        //type of Pattern (shading or tiling)
        final int shadingType= PatternObj.getInt(PdfDictionary.PatternType);
        
        // get optional matrix values
        
        final float[] inputs=PatternObj.getFloatArray(PdfDictionary.Matrix);
        
        if(inputs!=null){
            
            if(shadingType==1){
                final float[][] Nmatrix={{inputs[0],inputs[1],0f},{inputs[2],inputs[3],0f},{0f,0f,1f}};
                
                if(!newFlag && inputs[5]<0){
                        inputs[4]=0;
                        inputs[5]=0;
                }
                matrix=Nmatrix;
            }else{
                final float[][] Nmatrix={{inputs[0],inputs[1],0f},{inputs[2],inputs[3],0f},{inputs[4],inputs[5],1f}};
                
                colorsReversed = Nmatrix[2][0] < 0;
                
//                matrix=Matrix.multiply(Nmatrix,CTM); //comment out in order to match with spec
                matrix = Nmatrix;
            }
        }
        
        if(!newFlag){
            /**
             * setup appropriate type
             */
            if(shadingType == 1) { //tiling
                 currentColor = setupTilingNew(PatternObj,streamData);  
            } else if(shadingType == 2) { //shading                
                currentColor = setupShading(PatternObj,matrix);
            }
        }
    }
    
    @Override
    public BufferedImage getImageForPatternedShape(final Path path){
        //ensure read
        currentPdfFile.checkResolved(PatternObj);
        //lookup table
        final byte[] streamData=currentPdfFile.readStream(PatternObj,true,true,true, false,false, PatternObj.getCacheName(currentPdfFile.getObjectReader()));
        patternImage=getTilingPatternImage(PatternObj, streamData);
        
        if(fullImage == null){
            fullImage = createPatternCell();
        }
        
        
        final double minX = path.getBoundsInLocal().getMinX();
        final double minY = Math.abs(path.getBoundsInLocal().getMinY());
        final double width = path.getBoundsInLocal().getWidth();
        final double height = path.getBoundsInLocal().getHeight();
        final Rectangle bounds = new Rectangle((int)minX, (int)minY, (int)width, (int)height);
        final Rectangle rect = new Rectangle(fullImage.getWidth(), fullImage.getHeight());
        BufferedImage finalImage = new BufferedImage((int)(width + minX), (int)(height + minY), BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2 = finalImage.createGraphics();
        final AffineTransform defaultAff = g2.getTransform();
        
        for(int x = 0; x < finalImage.getWidth(); x+= fullImage.getWidth()){
            for(int y = 0; y < finalImage.getHeight(); y+= fullImage.getHeight()) {
                rect.setLocation(x, y);
                if(bounds.intersects(rect)){
                    g2.translate(x, y);
                    g2.drawImage(fullImage, null, null);
                    g2.setTransform(defaultAff);
                }
            }
        }
        
        finalImage = finalImage.getSubimage((int)minX,(int)minY, (int)width, (int)height);
        
        return finalImage;
    }
    
    private BufferedImage createPatternCell(){
//        boolean isRotated=matrix[1][0]!=0 && matrix[0][1]!=0 && matrix[0][0]!=0 && matrix[1][1]!=0;
//        boolean isSkewed=matrix!=null && matrix[0][0]>0 &&  matrix[0][1]<0  && matrix[1][0]>0 &&  matrix[1][1]>0;
        
        final float xstep = PatternObj.getFloatNumber(PdfDictionary.XStep);
        final float ystep = PatternObj.getFloatNumber(PdfDictionary.YStep);
        final float[] BBox=PatternObj.getFloatArray(PdfDictionary.BBox);
//        float[] matrix = PatternObj.getFloatArray(PdfDictionary.Matrix);
        final float width = (BBox[0] + xstep) - BBox[0];
        final float height = (BBox[1] + ystep) - BBox[1];
        final float tileW = xstep * matrix[0][0];
        final float tileH = ystep * Math.abs(matrix[1][1]);
        float dx,dy;
        
        //ignore slight rotations
//        if(isRotated && matrix[0][0]!=0 && matrix[0][0]<0.001 && matrix[1][1]!=0 && matrix[1][1]<0.001){
//            isRotated=false;
//            matrix[0][0]=-matrix[0][1];
//            matrix[1][1]=-matrix[1][0];
//
//            matrix[1][0]=0;
//            matrix[0][1]=0;
//        }
        
        dx=matrix[0][0];
        dy=matrix[1][1];
        
        if(dx==0) {
            dx=matrix[0][1];
        }
        if(dx<0) {
            dx=-dx;
        }
        
        if(dy==0) {
            dy=matrix[1][0];
        }
        if(dy<0) {
            dy=-dy;
        }
        
        dx *= xstep;
        dy *= ystep;
        final int xCount=(int)(xstep/dx);
        final int yCount=(int)(ystep/dy);
        int imgW=(int) xstep;
        int imgH=(int) xstep;
        if(xCount>0 && yCount>0){
            imgW=(int)((xCount+1)*dx);
            imgH=(int)((yCount+1)*dy);
        }
        final BufferedImage fullImage = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2 = fullImage.createGraphics();
        
//        float offX,offY,rotatedWidth,rotatedHeight;
//        // Setup for rotated tiles
//        if(isSkewed){
//            rotatedWidth=(xstep *matrix[0][0])+(ystep * matrix[1][0]);
//            rotatedHeight=(ystep *matrix[1][1])-(xstep *matrix[0][1]);
//        }else{
//            rotatedWidth=(xstep *matrix[0][0])-(ystep *matrix[1][0]);
//
//            if(matrix[1][1]>0 && matrix[0][1]>0)
//                rotatedHeight=(ystep *matrix[1][1]);
//            else
//                rotatedHeight=-(ystep *matrix[1][1])-(xstep *matrix[0][1]);
//        }
//        System.out.println(rotatedHeight + " " + rotatedWidth);
//
//        offX=(patternImage.getWidth()-rotatedHeight);
//        offY=(patternImage.getHeight()-rotatedWidth);
        
        
        
//        System.out.println(width + " " + height);
        
        final AffineTransform defaultAff = g2.getTransform();
        // tile*2 to avaoid gaps
        for(int x = 2; x <= width + tileW*2; x+=tileW){
            for(int y = 0; y <= height + tileH*2; y+=tileH){
//                g2.transform(new AffineTransform(matrix[0][0],matrix[0][1],matrix[1][0],matrix[1][1], x, y));
                g2.transform(new AffineTransform(matrix[0][0],0,0,matrix[1][1], x, y));
                g2.drawImage(patternImage, null, null);
                g2.setTransform(defaultAff);
            }
        }
        
        g2.transform(new AffineTransform(matrix[0][0],matrix[0][1],matrix[1][0],matrix[1][1], 0, 0));
        g2.drawImage(fullImage, null, null);
        g2.setTransform(defaultAff);
        
        return fullImage;
    }
    
    private BufferedImage getTilingPatternImage(final PdfObject PatternObj, final byte[] streamData) {
        
        final float[] BBox=PatternObj.getFloatArray(PdfDictionary.BBox);
        
        final int tileW=(int) (BBox[2]-BBox[0]);
        final int tileH=(int) (BBox[3]-BBox[1]);
        
        final BufferedImage image=new BufferedImage(tileW, tileH, BufferedImage.TYPE_INT_ARGB);
        
        /**
         * convert stream into an DynamicVector object we can then draw onto screen or tile
         */
        final ObjectStore localStore = new ObjectStore();
        final DynamicVectorRenderer glyphDisplay = decodePatternContent(PatternObj, new float[][]{{1,0,0},{0,1,0},{0,0,1}}, streamData, localStore);
        
        final Graphics2D g2=image.createGraphics();
        // g2.setClip(new Rectangle(0,0,tileW,tileH));
        glyphDisplay.setG2(g2);
        glyphDisplay.paint(null,new AffineTransform() ,null);
        
        return image;
    }
    
    
//    public static void write(BufferedImage img, String name){
//        try {
//            javax.imageio.ImageIO.write(img, "png", new java.io.File("C:\\Users\\suda\\Desktop\\created\\patterns\\images\\"+name+"-"+System.currentTimeMillis()+".png"));
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//    }

    private PdfPaint setupTilingNew(final PdfObject PatternObj, final byte[] streamData) {
      
        float[][] mm;
        AffineTransform affine = new AffineTransform();
        AffineTransform rotatedAffine = new AffineTransform();
        float[] inputs = PatternObj.getFloatArray(PdfDictionary.Matrix);
        if (inputs != null) {
            mm = new float[][]{{inputs[0], inputs[1], 0f}, {inputs[2], inputs[3], 0f}, {inputs[4], inputs[5], 1f}};
            affine = new AffineTransform(mm[0][0], mm[0][1], mm[1][0], mm[1][1], mm[2][0], mm[2][1]);
        } else {
            mm = new float[][]{{1f, 0f, 0f}, {0f, 1f, 0f}, {0f, 0f, 1f}};
        }
        
        
        boolean isRotated = affine.getShearX()!=0 || affine.getShearY()!=0;
        
        if(isRotated){
            rotatedAffine = affine;
            affine = new AffineTransform();
            mm = new float[][]{{1f, 0f, 0f}, {0f, 1f, 0f}, {0f, 0f, 1f}};
        }
        
        final float[] rawBBox = PatternObj.getFloatArray(PdfDictionary.BBox);

        final float xGap = Math.abs(rawBBox[2] - rawBBox[0]);
        final float yGap = Math.abs(rawBBox[1] - rawBBox[3]);

        GeneralPath rawPath = new GeneralPath();
        rawPath.moveTo(rawBBox[0], rawBBox[1]);
        rawPath.lineTo(rawBBox[2], rawBBox[1]);
        rawPath.lineTo(rawBBox[2], rawBBox[3]);
        rawPath.lineTo(rawBBox[0], rawBBox[3]);
        rawPath.lineTo(rawBBox[0], rawBBox[1]);
        rawPath.closePath();
        Shape rawShape = rawPath.createTransformedShape(affine);
        Rectangle2D rawRect = rawShape.getBounds2D();

        float rawXStep = PatternObj.getFloatNumber(PdfDictionary.XStep);
        rawXStep = (30000 > Short.MAX_VALUE || rawXStep < -30000) ? 0f : rawXStep;
        float rawYStep = PatternObj.getFloatNumber(PdfDictionary.YStep);
        rawYStep = (30000 > Short.MAX_VALUE || rawYStep < -30000) ? 0f : rawYStep;

        float[] bbox = new float[4];

        if (rawXStep < 0) {
            bbox[2] = xGap - rawXStep;
        } else {
            bbox[2] = rawXStep;
        }
        if (rawYStep < 0) {
            bbox[3] = yGap - rawYStep;
        } else {
            bbox[3] = rawYStep;
        }

        GeneralPath boxPath = new GeneralPath();
        boxPath.moveTo(bbox[0], bbox[1]);
        boxPath.lineTo(bbox[2], bbox[1]);
        boxPath.lineTo(bbox[2], bbox[3]);
        boxPath.lineTo(bbox[0], bbox[3]);
        boxPath.lineTo(bbox[0], bbox[1]);
        boxPath.closePath();
        Shape boxShape = boxPath.createTransformedShape(affine);
        Rectangle2D boxRect = boxShape.getBounds2D();

        double imageW = (Math.abs(boxRect.getX()) + boxRect.getWidth()) - (Math.abs(rawRect.getX()));
        double imageH = (Math.abs(boxRect.getY()) + boxRect.getHeight()) - (Math.abs(rawRect.getY()));

        imageW = rawXStep == 0 ? rawRect.getWidth() : imageW;
        imageH = rawYStep == 0 ? rawRect.getWidth() : imageH;

        imageW = imageW > 3000 ? 1500 : imageW;
        imageH = imageH > 3000 ? 1500 : imageH;
        
        //very small images return white
        if(imageW<0.5 && imageH<0.5){
            return new PdfColor(255,255,255);
        }

        int iw = (int) (imageW);
        iw = iw < 1 ? 1 : iw;
        int ih = (int) (imageH);
        ih = ih < 1 ? 1 : ih;

        //hack fix to odd_pattern.pdf file
        if (imageH < 1 && imageW < 2.5) {
            iw = 1;
        }
        
        
        
//        //example: scooby doo file has rotation so follow old method
//        if(affine.getShearX()!=0 || affine.getShearY()!=0){
//            return setupTiling(PatternObj, inputs, matrix, streamData);
//        }

       

        final ObjectStore localStore = new ObjectStore();
        BufferedImage image;
        final DynamicVectorRenderer glyphDisplay;

        if (affine.getScaleX() < 0 || affine.getScaleY() < 0) {
            glyphDisplay = decodePatternContent(PatternObj, mm, streamData, localStore);
            image = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g2 = image.createGraphics();
            glyphDisplay.setG2(g2);
            AffineTransform moveAffine = new AffineTransform();
            moveAffine.setToTranslation(-rawRect.getX(), -rawRect.getY());
            glyphDisplay.paint(null, moveAffine, null);

        } else {
            glyphDisplay = decodePatternContent(PatternObj, null, streamData, localStore);
            double[] rd = new double[6];
            affine.getMatrix(rd);
            rd[4] -= rawRect.getX();
            rd[5] -= rawRect.getY();
            AffineTransform rdAffine = new AffineTransform(rd);
            image = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g2 = image.createGraphics();
            glyphDisplay.setG2(g2);
            glyphDisplay.paint(null, rdAffine, null);

        }
        Rectangle2D fRect = new Rectangle2D.Double(rawRect.getX(), rawRect.getY(), imageW, imageH);
        
        if(isRotated){
//            System.out.println("**********************");
//            System.out.println("patternobj " + PatternObj.getObjectRefAsString());
//            System.out.println("rawbox " + rawBBox[0] + " " + rawBBox[1] + " " + rawBBox[2] + " " + rawBBox[3]);
//            System.out.println("bbox " + bbox[0] + " " + bbox[1] + " " + bbox[2] + " " + bbox[3]);
//            System.out.println(affine);
//            System.out.println("steps: " + rawXStep + " " + rawYStep);
//            System.out.println(rawRect);
//            System.out.println(boxRect);
//            System.out.println("image dim " + imageW + " " + imageH + " " + iw + " " + ih);
//            write(image, "rotated");
            return new ShearedTexturePaint(image, fRect, rotatedAffine);
            
        }

//        BufferedImage sub = new BufferedImage(1500, 1500, BufferedImage.TYPE_INT_ARGB);
//        final Graphics2D g3 = sub.createGraphics();
//        glyphDisplay.setG2(g3);
//        glyphDisplay.paint(null, null, null);

        return new PdfTexturePaint(image, fRect);
    }

    
    
    private DynamicVectorRenderer decodePatternContent(final PdfObject PatternObj, final float[][] matrix, final byte[] streamData, final ObjectStore localStore) {
        
        final PdfObject Resources=PatternObj.getDictionary(PdfDictionary.Resources);
        
        //decode and create graphic of glyph
        
        final PdfStreamDecoderForPattern glyphDecoder=new PdfStreamDecoderForPattern(currentPdfFile);
        glyphDecoder.setParameters(false,true,7,0,false,false);
        
        glyphDecoder.setObjectValue(ValueTypes.ObjectStore,localStore);
        
        //glyphDecoder.setMultiplier(multiplyer);
        
        //
        
        //T3Renderer glyphDisplay=new T3Display(0,false,20,localStore);
        final T3Renderer glyphDisplay=new PatternDisplay(0,false,20,localStore);
        glyphDisplay.setOptimisedRotation(false);
        
        try{
            glyphDecoder.setRenderer(glyphDisplay);
            
            /**read the resources for the page*/
            if (Resources != null){
                glyphDecoder.readResources(Resources,true);
            }
            
            /**
             * setup matrix so scales correctly
             **/
            final GraphicsState currentGraphicsState=new GraphicsState(0,0);
            glyphDecoder.setGS(currentGraphicsState);
            //multiply to get new CTM
            if(matrix!=null) {
                currentGraphicsState.CTM =matrix;
            }
            
            /**
             * add in a colour (may well need further development)
             */
            if(strokCol==null){
                glyphDecoder.setDefaultColors(gs.getStrokeColor(),gs.getNonstrokeColor());
            }else{
                glyphDecoder.setDefaultColors(strokCol,new PdfColor(0,255,0));
            }
            
            glyphDecoder.decodePageContent(currentGraphicsState, streamData);
            
            //
            
        } catch (final PdfException e) {
            //tell user and log
            if(LogWriter.isOutput()) {
                LogWriter.writeLog("Exception: "+e.getMessage());
            }
            //
        }
        
        
        //flush as image now created
        return glyphDisplay;
    }
    
    /**
     */
    private PdfPaint setupShading(final PdfObject PatternObj, final float[][] matrix) {
        
        /**
         * get the shading object
         */
        
        final PdfObject Shading=PatternObj.getDictionary(PdfDictionary.Shading);
        
        /**
         * work out colorspace
         */
        final PdfObject ColorSpace=Shading.getDictionary(PdfDictionary.ColorSpace);
        
        //convert colorspace and get details
        GenericColorSpace newColorSpace=ColorspaceFactory.getColorSpaceInstance(currentPdfFile, ColorSpace);
        
        //use alternate as preference if CMYK
        if(newColorSpace.getID()==ColorSpaces.ICC && ColorSpace.getParameterConstant(PdfDictionary.Alternate)==ColorSpaces.DeviceCMYK) {
            newColorSpace=new DeviceCMYKColorSpace();
        }
        
        return new ShadedPaint(Shading, isPrinting,newColorSpace, currentPdfFile,matrix,colorsReversed, CTM, false);
        
    }
}
