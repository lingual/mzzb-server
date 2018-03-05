package mingzuozhibi.action;

import mingzuozhibi.persist.disc.Disc;
import mingzuozhibi.persist.disc.Disc.DiscType;
import mingzuozhibi.persist.disc.Disc.UpdateType;
import mingzuozhibi.service.amazon.AmazonTaskService;
import mingzuozhibi.support.JsonArg;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static mingzuozhibi.action.SakuraController.DISC_COLUMNS_ADMIN_SET;
import static mingzuozhibi.persist.disc.Disc.UpdateType.Both;
import static mingzuozhibi.service.amazon.DocumentReader.getNode;
import static mingzuozhibi.service.amazon.DocumentReader.getText;

@RestController
public class DiscController extends BaseController {

    public static final String COLUMNS = "id,asin,title,titlePc,titleMo," +
            "thisRank,prevRank,nicoBook,totalPt,discType,updateType," +
            "releaseDate,createTime,updateTime,mofidyTime,surplusDays";

    public static final Set<String> COLUMNS_SET = buildSet(COLUMNS);

    public static Set<String> buildSet(String columns) {
        return Stream.of(columns.split(",")).collect(Collectors.toSet());
    }

    @Autowired
    private AmazonTaskService service;

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional
    @GetMapping(value = "/api/discs/{id}", produces = MEDIA_TYPE)
    public String getOne(@PathVariable Long id) {

        Disc disc = dao.get(Disc.class, id);
        if (disc == null) {
            if (LOGGER.isWarnEnabled()) {
                warnRequest("[获取碟片失败][指定的碟片Id不存在][Id={}]", id);
            }
            return errorMessage("指定的碟片Id不存在");
        }


        JSONObject result = disc.toJSON(COLUMNS_SET);
        if (LOGGER.isDebugEnabled()) {
            debugRequest("[获取碟片成功][碟片信息={}]", result);
        }
        return objectResult(result);
    }

    @Transactional
    @PreAuthorize("hasRole('BASIC')")
    @PutMapping(value = "/api/discs/{id}", produces = MEDIA_TYPE)
    public String setOne(@PathVariable Long id,
                         @JsonArg String titlePc,
                         @JsonArg String titleMo,
                         @JsonArg DiscType discType,
                         @JsonArg UpdateType updateType,
                         @JsonArg String releaseDate) {

        if (releaseDate.isEmpty()) {
            if (LOGGER.isWarnEnabled()) {
                warnRequest("[编辑碟片失败][发售日期不能为空]");
            }
            return errorMessage("发售日期不能为空");
        }

        LocalDate localDate;
        try {
            localDate = LocalDate.parse(releaseDate, formatter);
        } catch (DateTimeParseException e) {
            if (LOGGER.isWarnEnabled()) {
                warnRequest("[编辑碟片失败][发售日期格式不正确]");
            }
            return errorMessage("发售日期格式不正确");
        }

        Disc disc = dao.get(Disc.class, id);
        if (disc == null) {
            if (LOGGER.isWarnEnabled()) {
                warnRequest("[编辑列表失败][指定的碟片Id不存在][Id={}]", id);
            }
            return errorMessage("指定的碟片Id不存在");
        }

        JSONObject before = disc.toJSON(COLUMNS_SET);
        if (LOGGER.isDebugEnabled()) {
            debugRequest("[编辑碟片开始][修改前={}]", before);
        }

        disc.setTitlePc(titlePc);
        disc.setTitleMo(titleMo);
        disc.setDiscType(discType);
        disc.setUpdateType(updateType);
        disc.setReleaseDate(localDate);

        JSONObject result = disc.toJSON(COLUMNS_SET);
        if (LOGGER.isDebugEnabled()) {
            debugRequest("[编辑列表成功][修改后={}]", result);
        }
        return objectResult(result);
    }

    @Transactional
    @PreAuthorize("hasRole('BASIC')")
    @GetMapping(value = "/api/discs/search/{asin}", produces = MEDIA_TYPE)
    public String search(@PathVariable String asin) {
        AtomicReference<Disc> disc = new AtomicReference<>(dao.lookup(Disc.class, "asin", asin));
        StringBuilder error = new StringBuilder();
        if (disc.get() == null) {
            service.createDiscTask(asin, task -> {
                if (task.isDone()) {
                    Node node = getNode(task.getDocument(), "Items", "Item", "ItemAttributes");
                    String rankText = getText(task.getDocument(), "Items", "Item", "SalesRank");
                    if (node != null) {
                        Document itemAttributes = node.getOwnerDocument();
                        String title = getText(itemAttributes, "Title");
                        String group = getText(itemAttributes, "ProductGroup");
                        String release = getText(itemAttributes, "ReleaseDate");
                        Objects.requireNonNull(title);
                        Objects.requireNonNull(group);
                        DiscType type = getType(group, title);
                        boolean amazon = title.startsWith("【Amazon.co.jp限定】");
                        LocalDate releaseDate;
                        if (release != null) {
                            releaseDate = LocalDate.parse(release, formatter);
                        } else {
                            releaseDate = LocalDate.now();
                        }
                        Disc newDisc = new Disc(asin, title, type, Both, amazon, releaseDate);
                        if (rankText != null) {
                            newDisc.setThisRank(new Integer(rankText));
                        }
                        dao.save(newDisc);
                        disc.set(newDisc);
                    } else {
                        error.append(task.getErrorMessage());
                    }
                }

                synchronized (disc) {
                    disc.notify();
                }
            });

            try {
                synchronized (disc) {
                    TimeUnit.SECONDS.timedWait(disc, 20);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();

            }
        }
        if (disc.get() == null) {
            if (error.length() == 0) {
                return errorMessage("查询超时，你可以稍后再尝试");
            } else {
                return errorMessage(error.toString());
            }
        }

        JSONArray result = new JSONArray();
        result.put(disc.get().toJSON(DISC_COLUMNS_ADMIN_SET));
        return objectResult(result);
    }

    private DiscType getType(String group, String title) {
        switch (group) {
            case "Music":
                return DiscType.Cd;
            case "DVD":
                if (title.contains("Blu-ray")) {
                    return DiscType.Bluray;
                } else {
                    return DiscType.Dvd;
                }
            default:
                return DiscType.Other;
        }
    }

}
