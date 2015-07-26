package jags.ratpacktest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import ratpack.func.Action;
import ratpack.http.MediaType;
import ratpack.http.client.ReceivedResponse;
import ratpack.http.client.RequestSpec;
import ratpack.test.MainClassApplicationUnderTest;
import ratpack.test.http.TestHttpClient;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by jose abelardo gutierrez on 7/26/15.
 */
public class AppTest {
  
  private static final ObjectMapper mapper = new ObjectMapper();
  
  private static MainClassApplicationUnderTest aut;

  private static Configuration freemarkerCfg;
  
  private TestHttpClient client;

  @BeforeClass
  public static void beforeClass() throws IOException {
    aut = new MainClassApplicationUnderTest(App.class);
    freemarkerCfg = App.initFreemarker();
  }
  
  @AfterClass
  public static void afterClass() {
    aut.stop();
  }

  @Before
  public void before() {
    client = TestHttpClient.testHttpClient(aut);
  }

  @Test
  public void getHelloTest() {
    ReceivedResponse response = client.get("/hello");
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(response.getBody().getText()).isEqualTo("Hello, Ratpack");
  }

  @Test
  public void createBookmarkTest() throws Exception {
    ReceivedResponse response = client.get("/api/bookmarks");
    Bookmark[] bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    int lastSize = bookmarks.length;

    Bookmark  bookmark = new Bookmark("Test", "http://www.test.com");
    response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_CREATED);
    assertThat(response.getBody().getText()).matches("/api/bookmarks/\\d+");

    response = client.get("/api/bookmarks");
    bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    assertThat(bookmarks.length).isEqualTo(lastSize + 1);
  }

  private Action<RequestSpec> jsonRequestBody(Bookmark bookmark) throws JsonProcessingException {
    return requestSpec -> requestSpec.getBody()
                .type(MediaType.APPLICATION_JSON)
                .text(mapper.writeValueAsString(bookmark));
  }

  @Test
  public void getBookmarkTest() throws Exception {
    Bookmark  bookmark = new Bookmark("Test", "http://www.test.com");
    ReceivedResponse response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    String[] uri = response.getBody().getText().split("/");
    long id = Long.parseLong(uri[uri.length - 1]);

    response = client.get("/api/bookmarks/" + id);
    Bookmark retrieved = mapper.readValue(response.getBody().getText(), Bookmark.class);
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(retrieved.getId()).isEqualTo(id);
    assertThat(retrieved.getTitle()).isEqualTo("Test");
    assertThat(retrieved.getUrl()).isEqualTo("http://www.test.com");
  }

  @Test
  public void updateBookmarkTest() throws Exception {
    Bookmark  bookmark = new Bookmark("Test", "http://www.test.com");
    ReceivedResponse response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    String[] uri = response.getBody().getText().split("/");
    long id = Long.parseLong(uri[uri.length - 1]);

    bookmark.setTitle("Success");
    response = client.requestSpec(jsonRequestBody(bookmark)).put("/api/bookmarks/" + id);
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);

    response = client.get("/api/bookmarks/" + id);
    Bookmark retrieved = mapper.readValue(response.getBody().getText(), Bookmark.class);
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(retrieved.getId()).isEqualTo(id);
    assertThat(retrieved.getTitle()).isEqualTo("Success");
    assertThat(retrieved.getUrl()).isEqualTo("http://www.test.com");
  }

  @Test
  public void deleteBookmarkTest() throws Exception {
    Bookmark  bookmark = new Bookmark("Test", "http://www.test.com");
    ReceivedResponse response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    String[] uri = response.getBody().getText().split("/");
    long id = Long.parseLong(uri[uri.length - 1]);

    response = client.delete("/api/bookmarks/" + id);
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

    response = client.get("/api/bookmarks/" + id);
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
  }

  @Test
  public void getBookmarksOrderTest() throws Exception {
    // delete existing bookmarks
    ReceivedResponse response = client.get("/api/bookmarks");
    Bookmark[] bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    for (Bookmark bookmark: bookmarks) {
      client.delete("/api/bookmarks/" + bookmark.getId());
    }

    // create new ones with titles in reverse order of creation time
    char[] initials = "ZYXWVUTSRQPONMLKJIHGFEDCBA".toCharArray();
    for (char initial: initials) {
      String title = initial + "_Title";
      String url = "http://www.test.com/" + initial;
      Bookmark bookmark = new Bookmark(title, url);
      client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    }

    response = client.get("/api/bookmarks?order=title");
    Bookmark[] bookmarksOrderByTitle = mapper.readValue(response.getBody().getText(), Bookmark[].class);

    response = client.get("/api/bookmarks?order=creation_timestamp");
    Bookmark[] bookmarksOrderByCreationTimestamp = mapper.readValue(response.getBody().getText(), Bookmark[].class);

    assertThat(initials.length).isEqualTo(bookmarksOrderByTitle.length);
    assertThat(initials.length).isEqualTo(bookmarksOrderByCreationTimestamp.length);

    int byTitlePos = 0;
    int byCreationTimestampPos = initials.length - 1;
    for (int i = 0; i < initials.length / 2; i++) {
      Bookmark byTitle = bookmarksOrderByTitle[byTitlePos + i];
      Bookmark byCreationTimestamp = bookmarksOrderByCreationTimestamp[byCreationTimestampPos - i];
      assertThat(byTitle.getId()).isEqualTo(byCreationTimestamp.getId());
      assertThat(byTitle.getTitle()).isEqualTo(byCreationTimestamp.getTitle());
      assertThat(byTitle.getUrl()).isEqualTo(byCreationTimestamp.getUrl());
      assertThat(byTitle.getCreationTimestamp()).isEqualTo(byCreationTimestamp.getCreationTimestamp());
    }
  }

  @Test
  public void freemarkerCreateBookmarkTest() throws Exception {
    ReceivedResponse response = client.get("/api/bookmarks");
    Bookmark[] bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    int lastSize = bookmarks.length;

    Bookmark  bookmark = new Bookmark("Test", "http://www.test.com");
    ReceivedResponse createResponse =
        client.requestSpec(formRequestBody(bookmark)).post("/freemarker/bookmarks");

    assertThat(createResponse.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_CREATED);

    response = client.get("/api/bookmarks");
    bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    assertThat(bookmarks.length).isEqualTo(lastSize + 1);

    String renderedTemplate = renderFreeMarker(bookmarks);
    String actual = createResponse.getBody().getText();
    assertThat(actual).isEqualTo(renderedTemplate);
  }

  private String renderFreeMarker(Bookmark[] bookmarks) throws IOException, TemplateException {

    Map<String, Object> model = new HashMap<>();
    model.put("bookmarks", bookmarks);
    model.put("content_template", "bookmark_list.ftl");

    StringWriter stringWriter = new StringWriter();
    Template template = freemarkerCfg.getTemplate("index.ftl");
    template.process(model, stringWriter);
    return stringWriter.toString();
  }

  private Action<? super RequestSpec> formRequestBody(Bookmark bookmark) {
    return requestSpec -> {
      String form = String.format("title=%s&url=%s", bookmark.getTitle(), bookmark.getUrl());
      requestSpec.getBody()
          .type(MediaType.APPLICATION_FORM)
          .text(form);
    };
  }
}
