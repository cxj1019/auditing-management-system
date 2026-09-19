package com.accounting.firm.confirmation.service.impl;

import com.accounting.firm.common.ai.AiChatClient;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.common.storage.SupabaseStorageService;
import com.accounting.firm.confirmation.entity.Confirmation;
import com.accounting.firm.confirmation.entity.ConfirmationAttachment;
import com.accounting.firm.confirmation.mapper.ConfirmationAttachmentMapper;
import com.accounting.firm.confirmation.mapper.ConfirmationMapper;
import com.accounting.firm.confirmation.service.ConfirmationIntakeService;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.mapper.ProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 函证智能批量导入实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmationIntakeServiceImpl implements ConfirmationIntakeService {

    private static final int MAX_PAGES = 40;
    private static final float RENDER_DPI = 130;
    /** 每批送给视觉模型的页数（控制 token 体积） */
    private static final int AI_BATCH_SIZE = 8;

    private final AiChatClient aiChatClient;
    private final ConfirmationMapper confirmationMapper;
    private final ConfirmationAttachmentMapper attachmentMapper;
    private final SupabaseStorageService storageService;
    private final ProjectMapper projectMapper;

    // ================= 解析 =================

    @Override
    public List<Map<String, Object>> parseIntake(MultipartFile pdf) throws Exception {
        byte[] bytes = requirePdf(pdf);
        List<Map<String, Object>> groups = new ArrayList<>();

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            int pageCount = doc.getNumberOfPages();
            if (pageCount > MAX_PAGES) {
                throw new BusinessException("单次最多处理 " + MAX_PAGES + " 页，请分段上传");
            }
            PDFRenderer renderer = new PDFRenderer(doc);

            // 项目候选（编号 | 客户名），供 AI 建议归属
            List<Project> projects = projectMapper.selectList(null);
            StringBuilder projectList = new StringBuilder();
            for (Project p : projects) {
                projectList.append(p.getId()).append("=");
                if (p.getProjectNo() != null) projectList.append(p.getProjectNo());
                projectList.append("|");
                if (p.getName() != null) projectList.append(p.getName());
                projectList.append("\n");
            }

            // 分批识别，再按"单位名 + 连续页"合并
            List<String> pageUnits = new ArrayList<>();
            for (int from = 0; from < pageCount; from += AI_BATCH_SIZE) {
                int to = Math.min(from + AI_BATCH_SIZE, pageCount);
                List<byte[]> images = new ArrayList<>();
                for (int i = from; i < to; i++) {
                    images.add(renderPage(renderer, i));
                }
                String prompt = buildPrompt(from + 1, to, projectList.toString());
                String reply = aiChatClient.chatWithImages(prompt, images);
                pageUnits.addAll(parseUnitReply(reply, to - from));
            }

            // 分组：相邻页单位相同 → 同一份函证
            int currentStart = 0;
            for (int i = 1; i <= pageUnits.size(); i++) {
                boolean boundary = i == pageUnits.size()
                        || !sameUnit(pageUnits.get(i), pageUnits.get(currentStart));
                if (boundary) {
                    Map<String, Object> group = new LinkedHashMap<>();
                    List<Integer> pages = new ArrayList<>();
                    for (int p = currentStart; p < i; p++) pages.add(p + 1);
                    group.put("pages", pages);
                    String unit = pageUnits.get(currentStart);
                    group.put("unit", unit);
                    group.put("projectId", suggestProject(unit, projects));
                    group.put("projectName", "");
                    groups.add(group);
                    currentStart = i;
                }
            }
        }
        return groups;
    }

    private String buildPrompt(int fromPage, int toPage, String projectList) {
        return """
                这是询证函（审计函证）扫描件的第 %d 至 %d 页，共 %d 张图片，按顺序对应第 %d 页、第 %d 页……。
                请对每一张图片识别"被函证单位"（即函证抬头/落款的银行或公司名称，通常出现在标题"询证函"附近或函证对象处）。
                同一份函证可能有多页（银行询证函通常 3 页），请以页面上出现的单位全称为准。
                同时参考下方项目清单，为每个单位建议最匹配的项目 ID（无法确定则为 null）。

                项目清单（ID=编号|名称）：
                %s

                只输出 JSON，不要输出其他任何文字，格式：
                {"pages":[{"page":%d,"unit":"被函证单位全称","projectId":123 或 null}, ...]}
                """.formatted(fromPage, toPage, toPage - fromPage + 1, fromPage, fromPage + 1, projectList, fromPage, fromPage + 1);
    }

    /** 解析模型 JSON 回复；解析失败时该批按"未知单位"处理，保证流程可继续人工修正 */
    private List<String> parseUnitReply(String reply, int expected) {
        List<String> units = new ArrayList<>();
        try {
            int brace = reply.indexOf('{');
            int lastBrace = reply.lastIndexOf('}');
            String json = brace >= 0 && lastBrace > brace ? reply.substring(brace, lastBrace + 1) : reply;
            Matcher m = Pattern.compile("\"page\"\\s*:\\s*(\\d+)\\s*,\\s*\"unit\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
            Map<Integer, String> byPage = new LinkedHashMap<>();
            while (m.find()) {
                byPage.put(Integer.parseInt(m.group(1)), m.group(2).trim());
            }
            for (int i = 1; i <= expected; i++) {
                String unit = byPage.get(i);
                units.add(unit == null || unit.isBlank() ? "未识别单位" : unit);
            }
        } catch (Exception e) {
            log.warn("AI 回复解析失败: {}", e.getMessage());
            for (int i = 0; i < expected; i++) units.add("未识别单位");
        }
        return units;
    }

    /** 按单位名匹配项目：项目名或编号包含单位名（或反向包含）即视为匹配 */
    private Long suggestProject(String unit, List<Project> projects) {
        if (unit == null || "未识别单位".equals(unit)) return null;
        String compact = compact(unit);
        if (compact.length() < 4) return null;
        for (Project p : projects) {
            String name = compact(p.getName() == null ? "" : p.getName());
            if (name.length() >= 4 && (name.contains(compact) || compact.contains(name))) {
                return p.getId();
            }
        }
        return null;
    }

    private String compact(String s) {
        return s.replaceAll("[（）()\\s有限公司股份责任有限]", "");
    }

    private boolean sameUnit(String a, String b) {
        return compact(a).equals(compact(b));
    }

    private byte[] renderPage(PDFRenderer renderer, int index) throws IOException {
        BufferedImage image = renderer.renderImageWithDPI(index, RENDER_DPI);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return out.toByteArray();
    }

    // ================= 确认归档 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmIntake(MultipartFile pdf, List<Map<String, Object>> groups,
                                             SecurityUser currentUser) throws Exception {
        byte[] bytes = requirePdf(pdf);
        int created = 0;
        int attached = 0;
        int skipped = 0;
        String operator = currentUser.getNickname() != null ? currentUser.getNickname() : currentUser.getUsername();

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            for (Map<String, Object> group : groups) {
                Object pagesObj = group.get("pages");
                Object unitObj = group.get("unit");
                Object projObj = group.get("projectId");
                if (!(pagesObj instanceof List<?> pageList) || pageList.isEmpty() || unitObj == null) {
                    skipped++;
                    continue;
                }
                Long projectId = null;
                if (projObj instanceof Number n) {
                    projectId = n.longValue();
                }
                if (projectId == null) {
                    skipped++;
                    continue;
                }
                // 拆分页 → 新 PDF
                List<Integer> pageNumbers = pageList.stream()
                        .map(o -> Integer.parseInt(String.valueOf(o)))
                        .filter(pg -> pg >= 1 && pg <= doc.getNumberOfPages())
                        .toList();
                if (pageNumbers.isEmpty()) {
                    skipped++;
                    continue;
                }
                byte[] splitPdf = extractPages(doc, pageNumbers);

                // 目标函证记录：项目下同单位（精确/包含）优先，否则自动创建
                String unit = String.valueOf(unitObj);
                Confirmation target = findConfirmation(projectId, unit);
                if (target == null) {
                    target = new Confirmation();
                    target.setConfirmationNo("ZJ" + LocalDate.now().toString().replace("-", "")
                            + String.valueOf(System.currentTimeMillis()).substring(8));
                    target.setType("其他");
                    target.setConfirmationMethod("邮寄");
                    target.setTargetUnit(unit);
                    target.setSummary("智能批量导入自动创建（原单位名：" + unit + "）");
                    target.setProjectId(projectId);
                    target.setStatus(0);
                    confirmationMapper.insert(target);
                    created++;
                } else {
                    attached++;
                }

                // 原始函证附件
                String storedName = "confirmations/" + target.getId() + "/"
                        + java.util.UUID.randomUUID().toString().replace("-", "") + ".pdf";
                storageService.upload(storedName, splitPdf, "application/pdf");
                ConfirmationAttachment att = new ConfirmationAttachment();
                att.setConfirmationId(target.getId());
                att.setAttachmentType("original");
                att.setFileName(unit + "_" + pageNumbers.get(0) + "-" + pageNumbers.get(pageNumbers.size() - 1) + "页.pdf");
                att.setStoredName(storedName);
                att.setFileSize((long) splitPdf.length);
                att.setContentType("application/pdf");
                att.setCreateBy(currentUser.getUsername());
                attachmentMapper.insert(att);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("attached", attached);
        result.put("skipped", skipped);
        return result;
    }

    /** 项目下查找同单位函证：精确匹配 → 单位名互含 */
    private Confirmation findConfirmation(Long projectId, String unit) {
        List<Confirmation> list = confirmationMapper.selectList(new LambdaQueryWrapper<Confirmation>()
                .eq(Confirmation::getProjectId, projectId)
                .ne(Confirmation::getStatus, 3));
        for (Confirmation c : list) {
            if (c.getTargetUnit() != null && c.getTargetUnit().equals(unit)) return c;
        }
        String compactUnit = compact(unit);
        for (Confirmation c : list) {
            String t = compact(c.getTargetUnit() == null ? "" : c.getTargetUnit());
            if (t.length() >= 4 && compactUnit.length() >= 4 && (t.contains(compactUnit) || compactUnit.contains(t))) {
                return c;
            }
        }
        return null;
    }

    private byte[] extractPages(PDDocument source, List<Integer> pageNumbers) throws IOException {
        try (PDDocument out = new PDDocument()) {
            for (Integer pg : pageNumbers) {
                out.importPage(source.getPage(pg - 1));
                out.getPages().get(out.getNumberOfPages() - 1).setMediaBox(
                        source.getPage(pg - 1).getMediaBox() == null
                                ? PDRectangle.A4 : source.getPage(pg - 1).getMediaBox());
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            out.save(bos);
            return bos.toByteArray();
        }
    }

    private byte[] requirePdf(MultipartFile pdf) {
        if (pdf == null || pdf.isEmpty()) {
            throw new BusinessException("请上传扫描 PDF 文件");
        }
        String name = pdf.getOriginalFilename() == null ? "" : pdf.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".pdf")) {
            throw new BusinessException("仅支持 PDF 文件（扫描仪导出的多页 PDF）");
        }
        return pdfGetBytes(pdf);
    }

    private byte[] pdfGetBytes(MultipartFile pdf) {
        try {
            return pdf.getBytes();
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败");
        }
    }
}
