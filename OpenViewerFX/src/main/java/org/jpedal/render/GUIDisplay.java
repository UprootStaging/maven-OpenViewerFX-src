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
 * GUIDisplay.java
 * ---------------
 */

package org.jpedal.render;

import java.awt.Color;
import java.awt.Shape;
import org.jpedal.color.PdfColor;
import org.jpedal.constants.PDFImageProcessing;
import org.jpedal.exception.PdfException;
import org.jpedal.objects.GraphicsState;
import org.jpedal.objects.raw.PdfDictionary;
import org.jpedal.parser.Cmd;

/**
 *
 * functions shared by Swing and FX but not lower level Display implementations
 */
abstract class GUIDisplay extends BaseDisplay implements DynamicVectorRenderer{

    boolean isSwing=true; //reset in FXDisplay to false
    
    public void drawUserContent(final int[] type1, final Object[] obj, final Color[] colors) throws PdfException {
        
        /**
         * cycle through items and add to display - throw exception if not valid
         */
        final int count = type1.length;
        final boolean debug=false;
        int currentType;
        GraphicsState gs;
        
        for (int i = 0; i<count; i++) {
            
            currentType = type1[i];
            
            if(debug) {
                System.out.println(i + " " + getTypeAsString(currentType) + ' ' + ' ' + obj[i]);
            }
            
            switch(currentType){
                case DynamicVectorRenderer.FILLOPACITY:
                    setGraphicsState(GraphicsState.FILL, ((Float)obj[i]).floatValue(),PdfDictionary.Normal);
                    break;
                    
                case DynamicVectorRenderer.STROKEOPACITY:
                    setGraphicsState(GraphicsState.STROKE, ((Float)obj[i]).floatValue(),PdfDictionary.Normal);
                    break;
                    
                case DynamicVectorRenderer.STROKEDSHAPE:
                    gs=new GraphicsState();
                    gs.setFillType(GraphicsState.STROKE);
                    drawShape((Shape) obj[i],gs, Cmd.S);
                                        
                    break;
                    
                case DynamicVectorRenderer.FILLEDSHAPE:
                    gs=new GraphicsState();
                    gs.setFillType(GraphicsState.FILL);
                    gs.setNonstrokeColor(new PdfColor(colors[i].getRed(),colors[i].getGreen(),colors[i].getBlue()));
                    drawShape((Shape) obj[i],gs, Cmd.F);
                     
                    break;
                    
                case DynamicVectorRenderer.CUSTOM:
                    drawCustom(obj[i]);
                    
                    break;
                    
                case DynamicVectorRenderer.IMAGE:
                    final ImageObject imgObj=(ImageObject)obj[i];
                    gs=new GraphicsState();
                    
                    gs.CTM=new float[][]{ {imgObj.image.getWidth(),0,1}, {0,imgObj.image.getHeight(),1}, {0,0,0}};
                    
                    gs.x=imgObj.x;
                    gs.y=imgObj.y;
                    
                    drawImage(this.rawPageNumber,imgObj.image, gs,false,"extImg"+i, PDFImageProcessing.NOTHING, -1);
                    
                    break;
                    
                case DynamicVectorRenderer.STRING:
                    final TextObject textObj=(TextObject)obj[i];
                    gs=new GraphicsState();
                    final float fontSize=textObj.font.getSize();
                    final double[] afValues={fontSize,0f,0f,fontSize,0f,0f};
                    drawAffine(afValues);
                    
                    drawTR(GraphicsState.FILL);
                    gs.setTextRenderType(GraphicsState.FILL);
                    gs.setNonstrokeColor(new PdfColor(colors[i].getRed(),colors[i].getGreen(),colors[i].getBlue()));
                    drawText(null,textObj.text,gs,textObj.x,-textObj.y,textObj.font); //note y is negative
                    
                    break;
                    
                case 0:
                    break;
                    
                default:
                    throw new PdfException("Unrecognised type "+currentType);
            }
        }
    }
    
    private static String getTypeAsString(final int i) {
        
        String str = "Value Not set";
        
        switch(i){
            case DynamicVectorRenderer.FILLOPACITY:
                str="FILLOPACITY";
                break;
                
            case DynamicVectorRenderer.STROKEOPACITY:
                str="STROKEOPACITY";
                break;
                
            case DynamicVectorRenderer.STROKEDSHAPE:
                str="STROKEDSHAPE";
                break;
                
            case DynamicVectorRenderer.FILLEDSHAPE:
                str="FILLEDSHAPE";
                break;
                
            case DynamicVectorRenderer.CUSTOM:
                str="CUSTOM";
                break;
                
            case DynamicVectorRenderer.IMAGE:
                str="IMAGE";
                break;
                
            case DynamicVectorRenderer.STRING:
                str="String";
                break;
                
        }
        
        return str;
    }
  
}
