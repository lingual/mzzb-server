package mingzuozhibi.persist.disc;

import mingzuozhibi.persist.BaseModel;
import org.json.JSONObject;

import javax.persistence.Column;
import javax.persistence.Entity;
import java.time.LocalDateTime;

@Entity
public class DiscInfo extends BaseModel {

    private String asin;
    private String title;
    private LocalDateTime createTime;
    private boolean followed;

    public DiscInfo() {
    }

    public DiscInfo(String asin, String title) {
        this.asin = asin;
        this.title = title;
        this.createTime = LocalDateTime.now();
    }

    @Column(length = 20, nullable = false, unique = true)
    public String getAsin() {
        return asin;
    }

    public void setAsin(String asin) {
        this.asin = asin;
    }

    @Column(length = 500, nullable = false)
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Column(nullable = false)
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public boolean isFollowed() {
        return followed;
    }

    public void setFollowed(boolean followed) {
        this.followed = followed;
    }

    public JSONObject toJSON() {
        JSONObject object = new JSONObject();
        object.put("id", getId());
        object.put("asin", asin);
        object.put("title", title);
        object.put("createTime", toEpochMilli(createTime));
        object.put("followed", followed);
        return object;
    }

}
