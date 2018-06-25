package mingzuozhibi.action;

import org.jsoup.Jsoup;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class BmoeController extends BaseController {

    private static AtomicReference<String> current = new AtomicReference<>();

    @GetMapping(value = "/api/bmoe/current", produces = MEDIA_TYPE)
    public void current(HttpServletResponse response) throws IOException {
        String result = current.get();
        responseText(response, result == null ? "{code: -1}" : result);
    }

    public static void fetchCurrent() {
        for (int i = 0; i < 3; i++) {
            try {
                String body = Jsoup.connect("https://api.bilibili.com/pgc/moe/2018/1/api/schedule/current")
                        .ignoreContentType(true)
                        .timeout(10000)
                        .execute().body();
                current.set(body);
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
