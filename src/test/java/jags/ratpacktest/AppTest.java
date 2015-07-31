package jags.ratpacktest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jags.ratpacktest.dao.BookmarkDAO;
import jags.ratpacktest.dao.TagDAO;
import jags.ratpacktest.dao.TaggingDAO;
import jags.ratpacktest.domain.Bookmark;
import jags.ratpacktest.domain.Tag;
import org.assertj.core.api.StrictAssertions;
import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.util.StringUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.skife.jdbi.v2.DBI;
import ratpack.func.Action;
import ratpack.http.MediaType;
import ratpack.http.client.ReceivedResponse;
import ratpack.http.client.RequestSpec;
import ratpack.test.MainClassApplicationUnderTest;
import ratpack.test.http.TestHttpClient;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
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
  private static BookmarkDAO bookmarkDAO;
  private static TagDAO tagDAO;
  private static TaggingDAO taggingDAO;

  private TestHttpClient client;

  @BeforeClass
  public static void beforeClass() throws IOException {
    aut = new MainClassApplicationUnderTest(App.class);
    freemarkerCfg = App.initFreemarker();
    DBI dbi = new DBI(JdbcConnectionPool.create("jdbc:h2:mem:test", "sa", ""));
    bookmarkDAO = dbi.open(BookmarkDAO.class);
    tagDAO = dbi.open(TagDAO.class);
    taggingDAO = dbi.open(TaggingDAO.class);
  }

  @AfterClass
  public static void afterClass() {
    if (bookmarkDAO != null) {
      bookmarkDAO.close();
    }
    if (tagDAO != null) {
      tagDAO.close();
    }
    if (taggingDAO != null) {
      taggingDAO.close();
    }
    aut.stop();
  }

  @Before
  public void before() {
    client = TestHttpClient.testHttpClient(aut);
    taggingDAO.delete();
    tagDAO.delete();
    bookmarkDAO.delete();
  }

  @Test
  public void getHelloTest() {
    ReceivedResponse response = client.get("/hello");
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(response.getBody().getText()).isEqualTo("Hello, Ratpack");
  }

  @Test
  public void getBookmarkTest() throws Exception {
    long id = getNewBookmark().getId();

    ReceivedResponse response = client.get("/api/bookmarks/" + id);
    Bookmark retrieved = mapper.readValue(response.getBody().getText(), Bookmark.class);
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(retrieved.getId()).isEqualTo(id);
    assertThat(retrieved.getTitle()).isEqualTo("Test");
    assertThat(retrieved.getUrl()).isEqualTo("http://www.test.com");
  }

  @Test
  public void createBookmarkTest() throws Exception {
    int bookmarksLastSize = getBookmarkLastSize();
    int taggingsLastSize = getTaggingLastSize();
    String tagLabel = "tagLabel";
    Tag tag = tagDAO.findByLabel(tagLabel);
    assertThat(tag).isNull();

    Bookmark bookmark = new Bookmark("Test", "http://www.test.com", tagLabel);
    ReceivedResponse response =
        client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_CREATED);
    assertThat(response.getBody().getText()).matches("/api/bookmarks/\\d+");

    tag = tagDAO.findByLabel(tagLabel);
    assertThat(tag).isNotNull();
    assertThat(getBookmarkLastSize()).isEqualTo(bookmarksLastSize + 1);
    assertThat(getTaggingLastSize()).isEqualTo(taggingsLastSize + 1);
  }

  @Test
  public void nonexistentBookmarkUpdateTest() throws JsonProcessingException {
    Bookmark bookmark = getNewBookmark();
    long id = bookmark.getId() + 1;

    bookmark.setTitle("Updated");
    ReceivedResponse response =
        client.requestSpec(jsonRequestBody(bookmark)).put("/api/bookmarks/" + id);
    StrictAssertions.assertThat(response.getStatus().getCode())
        .isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
  }

  @Test
  public void updateBookmarkTest() throws Exception {
    Bookmark bookmark = getNewBookmark();
    int bookmarksLastSize = getBookmarkLastSize();
    int taggingsLastSize = getTaggingLastSize();
    int tagsLastSize = getTagLastSize();
    long id = bookmark.getId();
    String oldLabel = bookmark.getTags();
    String newLabel = "newLabel";
    
    bookmark.setTitle("Success");
    bookmark.setTags(newLabel);
    ReceivedResponse response =
        client.requestSpec(jsonRequestBody(bookmark)).put("/api/bookmarks/" + id);
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);

    Bookmark updated = bookmarkDAO.findById(id);
    assertThat(updated.getId()).isEqualTo(id);
    assertThat(updated.getTitle()).isEqualTo("Success");
    assertThat(updated.getUrl()).isEqualTo("http://www.test.com");

    assertThat(tagDAO.findByLabel(oldLabel)).isNull();
    assertThat(tagDAO.findByLabel(newLabel)).isNotNull();
    assertThat(getBookmarkLastSize()).isEqualTo(bookmarksLastSize);
    assertThat(getTaggingLastSize()).isEqualTo(taggingsLastSize);
    assertThat(getTagLastSize()).isEqualTo(tagsLastSize);
  }

  @Test
  public void deleteBookmarkTest() throws Exception {
    long id = getNewBookmark().getId();

    ReceivedResponse response = client.delete("/api/bookmarks/" + id);
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

    Bookmark bookmark = bookmarkDAO.findById(id);
    assertThat(bookmark).isNull();
  }

  @Test
  public void getBookmarksOrderTest() throws Exception {

    // create response with titles in reverse order of creation time
    char[] initials = "ZYXWVUTSRQPONMLKJIHGFEDCBA".toCharArray();
    for (char initial : initials) {
      String title = initial + "_Title";
      String url = "http://www.test.com/" + initial;
      Bookmark bookmark = new Bookmark(title, url);
      client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    }

    ReceivedResponse response = client.get("/api/bookmarks?order=title");
    Bookmark[] bookmarksOrderByTitle = mapper.readValue(response.getBody().getText(),
        Bookmark[].class);

    response = client.get("/api/bookmarks?order=creation_timestamp");
    Bookmark[] bookmarksOrderByCreationTimestamp = mapper.readValue(response.getBody().getText(),
        Bookmark[].class);

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
      assertThat(byTitle.getCreationTimestamp()).isEqualTo(
          byCreationTimestamp.getCreationTimestamp());
    }
  }

  @Test
  public void getBookmarksByTagTest() throws Exception {
    Bookmark bookmark1 = getNewBookmark("Title1", "http://www.test.com/1", "Tag1");
    Bookmark bookmark2 = getNewBookmark("Title2", "http://www.test.com/2", "Tag2");
    Bookmark bookmark3 = getNewBookmark("Title3", "http://www.test.com/3", "Tag1, Tag2");

    ReceivedResponse response = client.get("/api/bookmarks?tags=Tag1");
    Bookmark[] bookmarksTag1 = mapper.readValue(response.getBody().getText(),
        Bookmark[].class);

    response = client.get("/api/bookmarks?tags=Tag2");
    Bookmark[] bookmarksTag2 = mapper.readValue(response.getBody().getText(),
        Bookmark[].class);

    response = client.get("/api/bookmarks?tags=Tag1,Tag2");
    Bookmark[] bookmarksTag1AndTag2 = mapper.readValue(response.getBody().getText(),
        Bookmark[].class);

    assertThat(bookmarksTag1.length).isEqualTo(2);
    assertThat(bookmarksTag1).extracting("title").contains(bookmark1.getTitle(),
        bookmark3.getTitle());

    assertThat(bookmarksTag2.length).isEqualTo(2);
    assertThat(bookmarksTag2).extracting("title").contains(bookmark2.getTitle(),
        bookmark3.getTitle());

    assertThat(bookmarksTag1AndTag2.length).isEqualTo(3);
    assertThat(bookmarksTag1AndTag2).extracting("title")
        .contains(bookmark1.getTitle(), bookmark2.getTitle(), bookmark3.getTitle());
  }

  @Test
  public void getTagsTest() throws Exception {
    getNewBookmark("Title1", "http://www.test.com/1", "Tag1");
    getNewBookmark("Title2", "http://www.test.com/2", "Tag2");
    getNewBookmark("Title3", "http://www.test.com/3", "Tag3");

    ReceivedResponse response = client.get("/api/tags");
    Tag[] tags = mapper.readValue(response.getBody().getText(), Tag[].class);

    assertThat(tags).isNotNull();
    assertThat(tags.length).isEqualTo(3);
    assertThat(tags).extracting("label").contains("Tag1", "Tag2", "Tag3");
  }

  @Test
  public void freemarkerCreateBookmarkTest() throws Exception {
    int lastSize = getBookmarkLastSize();

    Bookmark bookmark = new Bookmark("Test", "http://www.test.com");
    ReceivedResponse createResponse =
        client.requestSpec(formRequestBody(bookmark)).post("/freemarker/bookmarks");

    assertThat(createResponse.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_CREATED);

    List<Bookmark> bookmarks = bookmarkDAO.findOrderByTitle();
    assertThat(bookmarks.size()).isEqualTo(lastSize + 1);

    String expected = renderFreeMarker(bookmarks.toArray(new Bookmark[bookmarks.size()]));
    String actual = createResponse.getBody().getText();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void freemarkerUpdateBookmarkTest() throws Exception {

    Bookmark bookmark = getNewBookmark();
    long id = bookmark.getId();
    int lastSize = getBookmarkLastSize();

    bookmark.setTitle("Updated");
    ReceivedResponse updateResponse =
        client.requestSpec(formRequestBody(bookmark, "put")).post("/freemarker/bookmarks/" + id);

    assertThat(updateResponse.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

    List<Bookmark> bookmarks = bookmarkDAO.findOrderByTitle();
    assertThat(bookmarks.size()).isEqualTo(lastSize);

    String expected = renderFreeMarker(bookmarks.toArray(new Bookmark[bookmarks.size()]));
    String actual = updateResponse.getBody().getText();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void freemarkerDeleteBookmarkTest() throws Exception {

    Bookmark bookmark = getNewBookmark();
    long id = bookmark.getId();
    int lastSize = getBookmarkLastSize();

    ReceivedResponse deleteResponse =
        client.requestSpec(formRequestBody(bookmark, "delete")).post("/freemarker/bookmarks/" + id);

    assertThat(deleteResponse.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

    List<Bookmark> bookmarks = bookmarkDAO.findOrderByTitle();
    assertThat(bookmarks.size()).isEqualTo(lastSize - 1);

    String expected = renderFreeMarker(bookmarks.toArray(new Bookmark[bookmarks.size()]));
    String actual = deleteResponse.getBody().getText();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void validateMissingTitleTest() throws JsonProcessingException {
    Bookmark bookmark = new Bookmark("", "http://www.test.com");
    ReceivedResponse response =
        client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    StrictAssertions.assertThat(response.getStatus().getCode())
        .isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
    StrictAssertions.assertThat(response.getBody().getText()).containsIgnoringCase("title");
  }

  @Test
  public void validateMissingUrlTest() throws JsonProcessingException {
    Bookmark bookmark = new Bookmark("Title", "");
    ReceivedResponse response =
        client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    StrictAssertions.assertThat(response.getStatus().getCode())
        .isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
    StrictAssertions.assertThat(response.getBody().getText()).containsIgnoringCase("url");
  }

  @Test
  public void validateInvalidUrlTest() throws JsonProcessingException {
    Bookmark bookmark = new Bookmark("Title", "url");
    ReceivedResponse response =
        client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    StrictAssertions.assertThat(response.getStatus().getCode())
        .isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
    StrictAssertions.assertThat(response.getBody().getText()).containsIgnoringCase("url");
  }

  @Test
  public void nonexistentFreemarkerBookmarkUpdateTest() throws JsonProcessingException {
    Bookmark bookmark = getNewBookmark();
    long id = bookmark.getId() + 1;

    bookmark.setTitle("Updated");
    ReceivedResponse updateResponse =
        client.requestSpec(formRequestBody(bookmark, "put")).post("/freemarker/bookmarks/" + id);
    StrictAssertions.assertThat(updateResponse.getStatus().getCode())
        .isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
  }
  
  private int getBookmarkLastSize() {
    return bookmarkDAO.count();
  }

  private int getTaggingLastSize() {
    return taggingDAO.count();
  }
  
  private int getTagLastSize() {
    return tagDAO.count();
  }

  private Action<RequestSpec> jsonRequestBody(Bookmark bookmark) throws JsonProcessingException {
    return requestSpec -> requestSpec.getBody()
        .type(MediaType.APPLICATION_JSON)
        .text(mapper.writeValueAsString(bookmark));
  }

  private Bookmark getNewBookmark() throws JsonProcessingException {
    return getNewBookmark("Test", "http://www.test.com", "tagLabel");
  }

  private Bookmark getNewBookmark(String title, String url, String tags)
      throws JsonProcessingException {
    Bookmark bookmark = new Bookmark(title, url, tags);
    bookmark.setId(bookmarkDAO.insert(bookmark));
    App.addTags(bookmark);
    return bookmark;
  }
  
  private String renderFreeMarker(Bookmark[] bookmarks) throws IOException, TemplateException {
    Map<String, Object> model = new HashMap<>();
    model.put("bookmarks", bookmarks);
    model.put("tags", tagDAO.findOrderByLabel());
    model.put("content_template", "bookmark_list.ftl");

    StringWriter stringWriter = new StringWriter();
    Template template = freemarkerCfg.getTemplate("index.ftl");
    template.process(model, stringWriter);
    return stringWriter.toString();
  }

  private Action<? super RequestSpec> formRequestBody(Bookmark bookmark) {
    return formRequestBody(bookmark, null);
  }

  private Action<? super RequestSpec> formRequestBody(Bookmark bookmark, String method) {
    return requestSpec -> {
      StringBuilder sb = new StringBuilder();
      if (!StringUtils.isNullOrEmpty(method)) {
        sb.append("_method=").append(method);
      }
      String title = bookmark.getTitle();
      if (!StringUtils.isNullOrEmpty(title)) {
        if (sb.length() > 0) { sb.append("&"); }
        sb.append("title=").append(title);
      }
      String url = bookmark.getUrl();
      if (!StringUtils.isNullOrEmpty(url)) {
        if (sb.length() > 0) { sb.append("&"); }
        sb.append("url=").append(url);
      }
      requestSpec.getBody()
          .type(MediaType.APPLICATION_FORM)
          .text(sb.toString());
    };
  }
}
