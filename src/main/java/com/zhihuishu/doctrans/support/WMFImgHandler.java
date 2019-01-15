package com.zhihuishu.doctrans.support;

import com.microsoft.schemas.vml.CTImageData;
import com.microsoft.schemas.vml.CTShape;
import com.zhihuishu.doctrans.model.WmfData;
import com.zhihuishu.doctrans.util.RegexHelper;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.drawingml.x2006.main.CTGraphicalObject;
import org.openxmlformats.schemas.drawingml.x2006.main.CTGraphicalObjectData;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveSize2D;
import org.openxmlformats.schemas.drawingml.x2006.picture.CTPicture;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTInline;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDrawing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static com.zhihuishu.doctrans.util.LengthUnitUtils.*;
import static org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_EMF;
import static org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_WMF;

/**
 * 提取document中WMF图片公式。方法是在提取处设置CTR占位符，并收集WMF图片元数据。
 */
public class WMFImgHandler {
    
    private XWPFDocument document;
    
    /**
     * placeholder map to WmfData
     */
    private Map<String, WmfData> wmfDatas = new HashMap<>();
    
    public WMFImgHandler(XWPFDocument document) {
        this.document = document;
    }
    
    /**
     * 提取document中的wmf元数据, 并设置占位符
     */
    public Map<String, WmfData> extractWMF() {
        List<IBodyElement> bodyElements = document.getBodyElements();
        for (IBodyElement bodyElement : bodyElements) {
            switch (bodyElement.getElementType()) {
                case PARAGRAPH:
                    XWPFParagraph paragraph = (XWPFParagraph) bodyElement;
                    extractWMFInParagraph(paragraph);
                    break;
                case TABLE:
                    XWPFTable table = (XWPFTable) bodyElement;
                    extractWMFInTable(table);
                    break;
                case CONTENTCONTROL:
                    // ignore
                    break;
                default:
                    break;
            }
        }
        return wmfDatas;
    }
    
    /**
     * 提取table中的wmf, 遍历每一个单元格cell
     */
    private void extractWMFInTable(XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        for (XWPFTableRow row : rows) {
            List<XWPFTableCell> cells = row.getTableCells();
            for (XWPFTableCell cell : cells) {
                extractWMFInCell(cell);
            }
        }
    }
    
    /**
     * 提取cell中的wmf, 注意cell中可能会嵌套表格
     */
    private void extractWMFInCell(XWPFTableCell cell) {
        List<XWPFTable> tables = cell.getTables();
        for (XWPFTable table : tables) {
            extractWMFInTable(table);
        }
        List<XWPFParagraph> paragraphs = cell.getParagraphs();
        for (XWPFParagraph paragraph : paragraphs) {
            extractWMFInParagraph(paragraph);
        }
    }
    
    /**
     * 提取paragraph中的wmf
     */
    private void extractWMFInParagraph(XWPFParagraph paragraph) {
        List<XWPFRun> runs = paragraph.getRuns();
        for (XWPFRun run : runs) {
            extractWMFInRun(run);
        }
    }
    
    /**
     * 提取run中的wmf
     */
    private void extractWMFInRun(XWPFRun run) {
        CTR ctr = run.getCTR();
        XmlCursor c = ctr.newCursor();
        c.selectPath("./*");
        while (c.toNextSelection()) {
            XmlObject o = c.getObject();
            if (o instanceof CTDrawing) {
                // <w:drawing>类型处理
                CTDrawing ctDrawing = (CTDrawing) o;
                CTInline[] ctInlines = ctDrawing.getInlineArray();
                // <wp:inline>
                for (CTInline ctInline : ctInlines) {
                    // <wp:extent>
                    // CTPositiveSize2D ctPositiveSize2D = ctInline.getExtent();
                    // ctPositiveSize2D.getCx();
                    
                    // <a:graphic>
                    CTGraphicalObject graphicalObject = ctInline.getGraphic();
                    
                    // <a:graphicData>
                    CTGraphicalObjectData graphicData = graphicalObject.getGraphicData();
                    XmlCursor cursor = graphicData.newCursor();
                    cursor.selectPath("./*");
                    while (cursor.toNextSelection()) {
                        XmlObject xmlObject = cursor.getObject();
                        // <pic:pic>
                        if (xmlObject instanceof CTPicture) {
                            CTPicture picture = (CTPicture) xmlObject;
                            // <a:ext>, 内含图片的高度宽度参数
                            CTPositiveSize2D ext = picture.getSpPr().getXfrm().getExt();
                            double width = emu2points(ext.getCx());
                            double height = emu2points(ext.getCy());
                            
                            String blipID = picture.getBlipFill().getBlip().getEmbed();
                            XWPFPictureData pictureData = document.getPictureDataByID(blipID);
                            String pictureName = pictureData.getFileName();
                            int pictureStyle = pictureData.getPictureType();
                            
                            // 只处理wmf格式的图片, 其它格式的图片交由xdocreport进行默认处理
                            if (pictureStyle == PICTURE_TYPE_EMF || pictureStyle == PICTURE_TYPE_WMF) {
                                String placeholder = PlaceholderHelper.createWMFPlaceholder(pictureName);
                                wmfDatas.put(placeholder, new WmfData(placeholder, pictureData.getData(), width, height));
                                createWMFPlaceholder(run, placeholder);
                                // 移除<w:drawing>，避免xdocreport对图片重复处理。现假设每个<w:r>下最多只有一个<w:drawing>，
                                // 若有特殊情况产生异常应修改remove的参数值
                                ctr.removeDrawing(0);
                            }
                        }
                    }
                }
            } else if (o instanceof CTObject) {
                // <w:object>类型处理
                CTObject object = (CTObject) o;
                XmlCursor w = object.newCursor();
                w.selectPath("./*");
                while (w.toNextSelection()) {
                    XmlObject xmlObject = w.getObject();
                    // <v:shape>, 里面一般都是wmf格式图片
                    if (xmlObject instanceof CTShape) {
                        parseWMFFromCTShape((CTShape) xmlObject, run);
                    }
                }
            } else if (o instanceof org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPicture) {
                // <w:pict>类型处理
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPicture pict =
                        (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPicture) o;
                XmlCursor w = pict.newCursor();
                w.selectPath("./*");
                while (w.toNextSelection()) {
                    XmlObject xmlObject = w.getObject();
                    // <v:shape>, 里面一般都是wmf格式图片
                    if (xmlObject instanceof CTShape) {
                        parseWMFFromCTShape((CTShape) xmlObject, run);
                    }
                }
            }
        }
    }
    
    // 从<v:shape>中提取wmf图片数据，并设置占位符
    private void parseWMFFromCTShape(CTShape ctShape, XWPFRun run) {
        CTImageData imageData = ctShape.getImagedataArray(0);
        String blipID = imageData.getId2();
        XWPFPictureData pictureData = document.getPictureDataByID(blipID);
        String pictureName = pictureData.getFileName();
        String placeholder = PlaceholderHelper.createWMFPlaceholder(pictureName);
    
        // 解析wmf的高宽样式
        Double[] styles = parseWMFStyle(ctShape.getStyle());
        Double width = styles[0];
        Double height = styles[1];
    
        wmfDatas.put(placeholder, new WmfData(placeholder, pictureData.getData(), width, height));
        createWMFPlaceholder(run, placeholder);
    }
    
    /**
     * 设置wmf图片占位符, 以便图片上传完成后进行替换
     */
    private void createWMFPlaceholder(XWPFRun run, String placeholder) {
        run.setText(placeholder);
    }
    
    private Double[] parseWMFStyle(String style) {
        Double[] styles = new Double[2];
        Matcher matcher;
        
        // 解析width样式，并将单位转为px
        try {
            if ((matcher = RegexHelper.widthValuePattern.matcher(style)).find()) {
                double num = Double.parseDouble(matcher.group(1));
                String measure = matcher.group(2);
                styles[0] = convertToPoints(measure, num);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        
        // 解析height样式，并将单位转为px
        try {
            if ((matcher = RegexHelper.heightValuePattern.matcher(style)).find()) {
                double num = Double.parseDouble(matcher.group(1));
                String measure = matcher.group(2);
                styles[1] = convertToPoints(measure, num);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        
        return styles;
    }
    
    private Double convertToPoints(String measure, double num) {
        Double points;
        switch (measure) {
            case INCH_UNIT:
                // points = inch2points(num); 样式中的英寸单位不够准确，先不读这个参数
                points = null;
                break;
            case PT_UNIT:
                points = pt2points(num);
                break;
            case PX_UNIT:
                points = num;
                break;
            default:
                points = null;
                break;
        }
        return points;
    }
}