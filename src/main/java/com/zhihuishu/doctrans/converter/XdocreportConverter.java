package com.zhihuishu.doctrans.converter;

import com.zhihuishu.doctrans.converter.support.ConvertSetting;
import com.zhihuishu.doctrans.util.FileUploader;
import com.zhihuishu.doctrans.util.RegexHelper;
import fr.opensagres.poi.xwpf.converter.xhtml.XHTMLConverter;
import fr.opensagres.poi.xwpf.converter.xhtml.XHTMLOptions;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.zhihuishu.doctrans.util.ImgConverter.FORMAT_PNG;
import static org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_EMF;
import static org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_WMF;

public class XdocreportConverter extends AbstractDocxConverter {
    
    private XWPFDocument document;
    
    /** 公式提取器 */
    private PlaceholderEquationExtractor equationExtracter = new PlaceholderEquationExtractor();
    
    public XdocreportConverter(InputStream inputStream, ConvertSetting setting) throws IOException {
        try (InputStream in = inputStream) {
            this.document = new XWPFDocument(in);
        }
        if (setting != null) {
            this.setting = setting;
        } else {
            this.setting = new ConvertSetting();
        }
    }
    
    public XdocreportConverter(File file, ConvertSetting setting) throws IOException {
        this(new FileInputStream(file), setting);
    }
    
    @Override
    public String convert() {
        String html;
        Instant convertTime = Instant.now();
        try (StringWriter htmlWriter = new StringWriter()) {
            if (logger.isDebugEnabled()) {
                logger.debug("文档初始xml:" + document.getDocument().xmlText() + "\n");
            }
            
            // 提取一般图片元数据
            Map<String, byte[]> imageBytes = new HashMap<>();
            List<XWPFPictureData> pictureDatas = document.getAllPictures();
            for (XWPFPictureData pictureData : pictureDatas) {
                int pictureType = pictureData.getPictureType();
                if (pictureType == PICTURE_TYPE_EMF || pictureType == PICTURE_TYPE_WMF) {
                    // ignore
                } else {
                    imageBytes.put(pictureData.getFileName(), pictureData.getData());
                }
            }
            
            // 提取wmf和omath公式元数据, 并设置占位符
            this.wmfDatas = equationExtracter.extractMathML(document);
            this.oMathDatas = equationExtracter.extractWMF(document);
            
            int imageNum = imageBytes.size() + this.wmfDatas.size() + this.oMathDatas.size();
            if (imageNum > 1000) {
                logger.warn("文档中包含图片过多:" + imageNum);
            }
            
            // 将公式转换为png
            Instant convertPNGTime = Instant.now();
            Map<String, byte[]> imageBytesOfWMF = convertWMFToPNG();
            Map<String, byte[]> imageBytesOfOMath = convertMathMLToPNG();
            long convertPNGUseTime = Duration.between(convertPNGTime, Instant.now()).toMillis();
            logger.info("公式转PNG耗时:" + convertPNGUseTime + "毫秒");
            
            if (logger.isDebugEnabled()) {
                logger.debug("抽取公式后xml:" + document.getDocument().xmlText() + "\n");
            }
            
            // 使用Xdocreport转换为html
            Instant convertHtmlTime = Instant.now();
            XHTMLOptions options = XHTMLOptions.create();
            options.setFragment(setting.isFragment());
            options.setIgnoreStylesIfUnused(true);
            XHTMLConverter xhtmlConverter = (XHTMLConverter) XHTMLConverter.getInstance();
            xhtmlConverter.convert(document, htmlWriter, options);
            long convertHtmlUseTime = Duration.between(convertHtmlTime, Instant.now()).toMillis();
            logger.info("html转换耗时:" + convertHtmlUseTime + "毫秒");
            
            html = htmlWriter.toString();
            
            // 将图片上传到OSS
            Instant uploadImgTime = Instant.now();
            logger.info("将" + imageNum + "张图片上传到OSS服务器");
            Map<String, String> imageUrls = FileUploader.uploadImageToOSS(imageBytes, null, true);
            Map<String, String> wmfImageUrls = FileUploader.uploadImageToOSS(imageBytesOfWMF, FORMAT_PNG, true);
            Map<String, String> oMathImageUrls = FileUploader.uploadImageToOSS(imageBytesOfOMath, FORMAT_PNG, true);
            long uploadImgUseTime = Duration.between(uploadImgTime, Instant.now()).toMillis();
            logger.info("上传图片耗时:" + uploadImgUseTime + "毫秒");
            
            // 网络图片URL替换
            html = replaceImgUrl(imageUrls, html);
            html = replaceWmfImgUrl(wmfImageUrls, html);
            html = replaceOmathImgUrl(oMathImageUrls, html);
            
            html = postProcessHtml(html);
        } catch (Exception e) {
            logger.error("文档转换出现异常", e);
            html = null;
        }
        long convertUseTime = Duration.between(convertTime, Instant.now()).toMillis();
        logger.info("文档转换总耗时:" + convertUseTime + "毫秒");
        return html;
    }
    
    protected String postProcessHtml(String orginHtml) {
        // 去掉div、span标签
        orginHtml = orginHtml.replaceAll("<\\/?(div|span|br\\/)[\\s\\S]*?>", "");
        // 使用<br>标签替代<p>标签换行方式
        List<String> ps = new ArrayList<>();
        Pattern pElementPattern = RegexHelper.getPElementPattern();
        Matcher matcher = pElementPattern.matcher(orginHtml);
        while (matcher.find()) {
            ps.add(matcher.group(1));
        }
        
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < ps.size(); i++) {
            html.append(ps.get(i));
            if (i != ps.size() - 1) {
                html.append("<br>");
            }
        }
        return html.toString();
    }
}
