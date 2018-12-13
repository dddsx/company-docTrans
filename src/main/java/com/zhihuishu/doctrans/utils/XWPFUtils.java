package com.zhihuishu.doctrans.utils;

import com.microsoft.schemas.vml.CTShape;
import com.zhihuishu.doctrans.model.Shape;
import net.sourceforge.jeuclid.context.LayoutContextImpl;
import net.sourceforge.jeuclid.context.Parameter;
import net.sourceforge.jeuclid.converter.Converter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.officeDocument.x2006.math.CTOMath;
import org.openxmlformats.schemas.officeDocument.x2006.math.CTOMathPara;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class XWPFUtils {
    
    private static int mathmlNum = 0;

    /**
     * 读取段落中的图片信息，为矢量图设置占位符，并返回矢量图索引列表
     */
    public static List<Shape> extractShapeInParagraph(XWPFParagraph paragraph) {
        // printParagraphAttr(paragraph);
        List<Shape> shapeList = new ArrayList<>();

        // 段落中所有XWPFRun
        List<XWPFRun> runList = paragraph.getRuns();
        for (XWPFRun run : runList) {
            // XWPFRun是POI对xml元素解析后生成的自己的属性，无法通过xml解析，需要先转化成CTR
            CTR ctr = run.getCTR();
            // 对子元素进行遍历
            XmlCursor c = ctr.newCursor();
            // 这个就是拿到所有的子元素：
            c.selectPath("./*");
            while (c.toNextSelection()) {
                XmlObject o = c.getObject();
                // 如果子元素是<w:drawing>这样的形式，使用CTDrawing保存图片
/*                if (o instanceof CTDrawing) {
                    CTDrawing drawing = (CTDrawing) o;
                    CTInline[] ctInlines = drawing.getInlineArray();
                    for (CTInline ctInline : ctInlines) {
                        CTGraphicalObject graphic = ctInline.getGraphic();
                        //
                        XmlCursor cursor = graphic.getGraphicData().newCursor();
                        cursor.selectPath("./*");
                        while (cursor.toNextSelection()) {
                            XmlObject xmlObject = cursor.getObject();
                            // 如果子元素是<pic:pic>这样的形式
                            if (xmlObject instanceof CTPicture) {
                                org.openxmlformats.schemas.drawingml.x2006.picture.CTPicture picture = (org.openxmlformats.schemas.drawingml.x2006.picture.CTPicture) xmlObject;
                                //拿到元素的属性
                                imageBundleList.add(picture.getBlipFill().getBlip().getEmbed());
                            }
                        }
                    }
                }*/

                // 使用CTObject读取<w:object>形式的图片
                if (o instanceof CTObject) {
                    CTObject object = (CTObject) o;
                    // System.out.println(object);
                    XmlCursor w = object.newCursor();
                    w.selectPath("./*");
                    while (w.toNextSelection()) {
                        XmlObject xmlObject = w.getObject();
                        if (xmlObject instanceof CTShape) {
                            Shape s = new Shape();
                            CTShape shape = (CTShape) xmlObject;
                            String ref = shape.getImagedataArray()[0].getId2();
                            // 设置占位标记
                            run.setText(createRefPlaceholder(ref));
                            s.setRef(ref);
                            s.setStyle(shape.getStyle());
                            shapeList.add(s);
                        }
                    }
                }
            }
        }
        return shapeList;
    }
    
    public static void extractMathMLInParagraph(XWPFParagraph p) {
//        List<CTOMath> ctoMathList = p.getCTP().getOMathList();
//        List<CTOMathPara> ctoMathParaList = p.getCTP().getOMathParaList();
//        for (CTOMath ctoMath : ctoMathList) {
//            try {
//               convertOmathToPNG(ctoMath);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//        for (CTOMathPara ctoMathPara : ctoMathParaList) {
//            for (CTOMath ctoMath : ctoMathPara.getOMathList()) {
//                try {
//                    convertOmathToPNG(ctoMath);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        }
    }

    public static String createRefPlaceholder(String ref){
        return "{sharp:" + ref + "}";
    }
    
    private static void printParagraphAttr(XWPFParagraph paragraph) {
        Class clazz = paragraph.getClass();
        for (Method method : clazz.getMethods()){
            if(!method.getName().startsWith("get")){
                continue;
            }
            String returnTypeName = method.getGenericReturnType().getTypeName();
            if(returnTypeName.equals(String.class.getTypeName()) || returnTypeName.equals(BigInteger.class.getTypeName())
                    || returnTypeName.equals("int")) {
                if(method.getParameterTypes().length == 0) {
                    System.out.print(method.getName().substring(3) + ":");
                    try {
                        System.out.println(method.invoke(paragraph));
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
