package com.zhihuishu.doctrans;

import com.zhihuishu.doctrans.model.Shape;
import com.zhihuishu.doctrans.support.BatikWMFConverter;
import com.zhihuishu.doctrans.support.PNGConverter;
import com.zhihuishu.doctrans.support.WMFConverter;
import com.zhihuishu.doctrans.support.Constant;
import com.zhihuishu.doctrans.utils.MyFileUtil;
import com.zhihuishu.doctrans.utils.XWPFUtils;
import fr.opensagres.poi.xwpf.converter.xhtml.Base64EmbedImgManager;
import fr.opensagres.poi.xwpf.converter.xhtml.XHTMLConverter;
import fr.opensagres.poi.xwpf.converter.xhtml.XHTMLOptions;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DocxToHtmlConverter {

    private static final String HTML_PATH = "/data/html/";

    private static final String IMAGE_PATH = "/data/image/word/media/";

    /**
     * 将docx文件转换为带图文的html
     * @param inputStream docx文件输入流
     * @return html字符串
     */
    public static String docx2html(InputStream inputStream) {
        long startTime = System.currentTimeMillis();
        String htmlResult = "";
        File htmlOutputFile = new File(HTML_PATH + UUID.randomUUID().toString() + Constant.EXT_HTML);
        try (InputStream docxInputStream = inputStream;
             Writer writer = new OutputStreamWriter(FileUtils.openOutputStream(htmlOutputFile), StandardCharsets.UTF_8))
        {
            long currentTime = System.currentTimeMillis();
            XWPFDocument document = new XWPFDocument(docxInputStream);
            System.out.println("解析docx耗时:" + (System.currentTimeMillis() - currentTime) + "ms");
            List<XWPFParagraph> paragraphList = document.getParagraphs();
            Map<String, Shape> shapeMap = new HashMap<>();
            for (XWPFParagraph paragraph : paragraphList) {
                List<Shape> shapeList = XWPFUtils.extractShapeInParagraph(paragraph);
                XWPFUtils.extractMathMLInParagraph(paragraph);
                if(shapeList == null || shapeList.size() == 0){
                    continue;
                }
                for (Shape shape : shapeList) {
                    XWPFPictureData pictureData = document.getPictureDataByID(shape.getRef());
                    String imageName = pictureData.getFileName();
                    shape.setImgName(imageName);
                    shapeMap.put(imageName, shape);
                }
            }

            List<File> imgFileList = new ArrayList<>();
            List<XWPFPictureData> pictures = document.getAllPictures();
            for (XWPFPictureData picture : pictures) {
                try {
                    File img = new File(IMAGE_PATH + picture.getFileName());
                    imgFileList.add(img);
                    FileUtils.writeByteArrayToFile(img, picture.getData());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            XHTMLOptions options = XHTMLOptions.create();
            options.setImageManager(new Base64EmbedImgManager());
            options.setFragment(true);
            options.setIgnoreStylesIfUnused(true);
            currentTime = System.currentTimeMillis();
            XHTMLConverter xhtmlConverter = (XHTMLConverter) XHTMLConverter.getInstance();
            xhtmlConverter.convert(document, writer, options);
            System.out.println("docx转html耗时:" + (System.currentTimeMillis() - currentTime) + "ms");
            htmlResult = FileUtils.readFileToString(htmlOutputFile, "UTF-8");
    
            WMFConverter wmfConverter = new BatikWMFConverter();
            for (File imgFile : imgFileList) {
                String imgName = imgFile.getName();
                if(FilenameUtils.isExtension(imgName, Constant.FORMAT_WMF)){
                    File svgFile = new File(IMAGE_PATH, FilenameUtils.getBaseName(imgName) + Constant.EXT_SVG);
                    File pngFile = new File(IMAGE_PATH, FilenameUtils.getBaseName(imgName) + Constant.EXT_PNG);
                    currentTime = System.currentTimeMillis();
                    wmfConverter.convertToSVG(imgFile, svgFile);
                    PNGConverter.convertSvg2Png(svgFile, pngFile);
                    System.out.println("wmf转png耗时:" + (System.currentTimeMillis() - currentTime) + "ms");
                    currentTime = System.currentTimeMillis();
                    String imgOssUrl = MyFileUtil.uploadFileToOSS(pngFile);
                    System.out.println("上传图片耗时:" + (System.currentTimeMillis() - currentTime) + "ms");
                    svgFile.delete();
                    pngFile.delete();
                    Shape shape = shapeMap.get(imgName);
                    String imgRefPlaceholder = XWPFUtils.createRefPlaceholder(shape.getRef());
                    htmlResult = htmlResult.replace(imgRefPlaceholder, createImgTag(imgOssUrl, shape.getStyle()));
                } else {
                    currentTime = System.currentTimeMillis();
                    String imgOssUrl = MyFileUtil.uploadFileToOSS(imgFile);
                    System.out.println("上传图片耗时:" + (System.currentTimeMillis() - currentTime) + "ms");
                    String imgHtmlUrl = File.separator + imgFile.getName();
                    htmlResult = htmlResult.replace(imgHtmlUrl, imgOssUrl);
                }
                imgFile.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            htmlOutputFile.delete();
        }
        System.out.println("总耗时:" + (System.currentTimeMillis() - startTime) + "ms");
        return htmlResult;
    }

    private static String createImgTag(String url, String style){
        if(url == null){
            url = "";
        }
        if(style == null){
            style = "";
        }
        return "<img src=\"" + url + "\" style=\"" + style + "\">";
    }
}
