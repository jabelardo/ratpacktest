package jags.ratpacktest;

import static ratpack.jackson.Jackson.fromJson;
import static ratpack.jackson.Jackson.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.util.StringUtils;
import org.skife.jdbi.v2.DBI;
import ratpack.form.Form;
import ratpack.handling.Context;
import ratpack.http.MediaType;
import ratpack.jackson.Jackson;
import ratpack.render.RendererSupport;
import ratpack.server.RatpackServer;
import ratpack.util.MultiValueMap;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.sql.DataSource;

public class App {

  private static DataSource ds = JdbcConnectionPool.create("jdbc:h2:mem:test", "sa", "");
  private static DBI dbi = new DBI(ds);
  private static Configuration freemarkerConfig;

  public static void main(String[] args) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new Jdk8Module());
    mapper.enable(SerializationFeature.INDENT_OUTPUT);

    initDB();
    freemarkerConfig = initFreemarker();

    RatpackServer.start(server -> server
            .registryOf(
                registrySpec -> Jackson.Init.register(registrySpec, mapper, mapper.writer()))

            .handlers(chain -> chain

                    .get("", ctx -> ctx.redirect("freemarker/bookmarks"))

                    .get("hello", ctx -> ctx.render("Hello, Ratpack"))

                    .path("api/bookmarks", ctx -> ctx
                        .byMethod(method -> method
                            .post(() -> createBookmark(ctx))
                            .get(() -> getBookmarks(ctx))))

                    .path("api/bookmarks/:id", ctx -> ctx
                        .byMethod(method -> method
                            .delete(() -> deleteBookmark(ctx))
                            .put(() -> updateBookmark(ctx))
                            .get(() -> getBookmark(ctx))))

                    .get("api/tags", App::getTags)

                    .register(new FreemarkerRenderer().register())

                    .path("freemarker/bookmarks", ctx -> ctx
                        .byMethod(method -> method
                            .get(() -> freemarkerBookmarkList(ctx))
                            .post(() -> freemarkerCreateBookmark(ctx))))

                    .get("freemarker/bookmarks/new", App::freemarkerBookmarkNew)

                    .path("freemarker/bookmarks/:id", ctx -> ctx
                        .byMethod(method -> method
                            .get(() -> freemarkerBookmarkEdit(ctx))
                            .post(() -> freemarkerUpdateOrDeleteBookmark(ctx))))
            )
    );
  }

  private static void deleteBookmark(Context ctx) {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      long id = Long.parseLong(ctx.getPathTokens().get("id"));
      deleteTags(id);
      dao.deleteById(id);
      ctx.getResponse().status(HttpURLConnection.HTTP_OK);
      ctx.getResponse().send();
    }
  }

  private static void getBookmark(Context ctx) {
    try (BookmarkDAO bookmarkDAO = dbi.open(BookmarkDAO.class)) {
      long id = Long.parseLong(ctx.getPathTokens().get("id"));
      Bookmark bookmark = bookmarkDAO.findById(id);
      if (bookmark == null) {
        ctx.getResponse().status(HttpURLConnection.HTTP_NOT_FOUND);
        ctx.getResponse().send();
      } else {
        setTags(bookmark);
        ctx.render(json(bookmark));
      }
    }
  }

  private static void updateBookmark(Context ctx) throws Exception {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      Bookmark bookmark = ctx.parse(fromJson(Bookmark.class));
      long id = Long.parseLong(ctx.getPathTokens().get("id"));
      if (validateForUpdate(ctx, bookmark)) {
        Bookmark existent = dao.findById(id);
        if (existent == null) {
          ctx.getResponse().status(HttpURLConnection.HTTP_NOT_FOUND);
          ctx.getResponse().send();
        } else {
          existent.setTitle(bookmark.getTitle());
          existent.setUrl(bookmark.getUrl());
          existent.setTags(bookmark.getTags());
          dao.update(existent);
          addTags(existent);
          ctx.getResponse().status(HttpURLConnection.HTTP_NO_CONTENT);
          ctx.getResponse().send();
        }
      }
    }
  }

  private static void initDB() {
    try (BookmarkDAO bookmarkDAO = dbi.open(BookmarkDAO.class);
         TagDAO tagDAO = dbi.open(TagDAO.class);
         TaggingDAO taggingDAO = dbi.open(TaggingDAO.class)) {
      bookmarkDAO.createBookmarkTable();
      tagDAO.createTagTable();
      taggingDAO.createTaggingTable();
    } catch (Exception ignored) {
    }
  }

  private static void getTags(Context ctx) {
    try (TagDAO dao = dbi.open(TagDAO.class)) {
      ctx.render(json(dao.findOrderByLabel()));
    }
  }

  private static void getBookmarks(Context ctx) {
    MultiValueMap<String, String> params = ctx.getRequest().getQueryParams();
    if ("creation_timestamp".equals(params.get("order"))) {
      getBookmarksOrderByByCreationTimestamp(ctx);
    } else {
      getBookmarksOrderByTitle(ctx);
    }
  }

  private static void getBookmarksOrderByTitle(Context ctx) {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      MultiValueMap<String, String> params = ctx.getRequest().getQueryParams();
      String tagsStr = params.get("tags");
      if (StringUtils.isNullOrEmpty(tagsStr)) {
        ctx.render(json(dao.findOrderByTitle()));
      } else {
        Set<String> tags = getTagSet(tagsStr);
        ctx.render(json(dao.findByTagLabelsOrderByTitle(tags)));
      }
    }
  }

  private static void getBookmarksOrderByByCreationTimestamp(Context ctx) {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      MultiValueMap<String, String> params = ctx.getRequest().getQueryParams();
      String tagsStr = params.get("tags");
      if (StringUtils.isNullOrEmpty(tagsStr)) {
        ctx.render(json(dao.findOrderByCreationTimestamp()));
      } else {
        Set<String> tags = getTagSet(tagsStr);
        ctx.render(json(dao.findByTagLabelsOrderByCreationTimestamp(tags)));
      }
    }
  }

  private static void createBookmark(Context ctx) throws Exception {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      Bookmark bookmark = ctx.parse(fromJson(Bookmark.class));
      if (validateForCreate(ctx, bookmark)) {
        Long bookmarkId = dao.insert(bookmark);
        bookmark.setId(bookmarkId);
        addTags(bookmark);
        ctx.getResponse().status(HttpURLConnection.HTTP_CREATED);
        ctx.getResponse().send("/api/bookmarks/" + bookmarkId);
      }
    }
  }

  private static boolean validateForUpdate(Context ctx, Bookmark bookmark) {
    if (StringUtils.isNullOrEmpty(bookmark.getTitle())) {
      ctx.getResponse().status(HttpURLConnection.HTTP_BAD_REQUEST);
      ctx.getResponse().send("title can't be empty");
      return false;
    }
    if (StringUtils.isNullOrEmpty(bookmark.getUrl())) {
      ctx.getResponse().status(HttpURLConnection.HTTP_BAD_REQUEST);
      ctx.getResponse().send("url can't be empty");
      return false;
    } else {
      try {
        new URL(bookmark.getUrl());
      } catch (MalformedURLException e) {
        ctx.getResponse().status(HttpURLConnection.HTTP_BAD_REQUEST);
        ctx.getResponse().send("url is not valid");
        return false;
      }
    }
    return true;
  }

  private static boolean validateForCreate(Context ctx, Bookmark bookmark) {
    if (bookmark.getId() != null) {
      ctx.getResponse().status(HttpURLConnection.HTTP_BAD_REQUEST);
      ctx.getResponse().send("id must be null");
      return false;
    }
    return validateForUpdate(ctx, bookmark);
  }

  public static Configuration initFreemarker() throws IOException {
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_22);
    cfg.setClassForTemplateLoading(App.class, "freemarker");
    cfg.setDefaultEncoding("UTF-8");
    // During web page *development* TemplateExceptionHandler.HTML_DEBUG_HANDLER is better.
    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
    // cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    return cfg;
  }

  private static void freemarkerBookmarkList(Context ctx) {
    try (BookmarkDAO bookmarkDAO = dbi.open(BookmarkDAO.class);
         TagDAO tagDAO = dbi.open(TagDAO.class)) {
      MultiValueMap<String, String> params = ctx.getRequest().getQueryParams();
      String tagsStr = params.get("tags");
      List<Bookmark> bookmarks;
      if (StringUtils.isNullOrEmpty(tagsStr)) {
        bookmarks = bookmarkDAO.findOrderByTitle();
      } else {
        Set<String> tags = getTagSet(tagsStr);
        bookmarks = bookmarkDAO.findByTagLabelsOrderByTitle(tags);
      }
      FreemarkerModel model = new FreemarkerModel();
      model.put("bookmarks", bookmarks);
      model.put("tags", tagDAO.findOrderByLabel());
      model.put("content_template", "bookmark_list.ftl");
      ctx.render(model);
    }
  }

  private static void freemarkerBookmarkNew(Context ctx) {
    FreemarkerModel model = new FreemarkerModel();
    model.put("bookmark", new Bookmark());
    model.put("content_template", "bookmark_form_new.ftl");
    ctx.render(model);
  }

  private static void freemarkerBookmarkEdit(Context ctx) {
    try (BookmarkDAO bookmarkDAO = dbi.open(BookmarkDAO.class)) {
      long id = Long.parseLong(ctx.getPathTokens().get("id"));
      FreemarkerModel model = new FreemarkerModel();
      Bookmark bookmark = bookmarkDAO.findById(id);
      setTags(bookmark);
      model.put("bookmark", bookmark);
      model.put("content_template", "bookmark_form_edit.ftl");
      ctx.render(model);
    }
  }

  private static void setTags(Bookmark bookmark) {
    try (TagDAO tagDAO = dbi.open(TagDAO.class)) {
      List<String> labels = tagDAO.findLabelsByBookmarkId(bookmark.getId());
      StringBuilder tags = new StringBuilder();
      for (int i = 0; i < labels.size(); i++) {
        tags.append(labels.get(i));
        if (i < labels.size() - 1) {
          tags.append(",");
        }
      }
      bookmark.setTags(tags.toString());
    }
  }

  private static void freemarkerCreateBookmark(Context ctx) throws Exception {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      Form form = ctx.parse(Form.class);
      String title = form.get("title");
      String url = form.get("url");
      String tags = form.get("tags");
      Bookmark bookmark = new Bookmark(title, url, tags);
      if (validateForCreate(ctx, bookmark)) {
        bookmark.setId(dao.insert(bookmark));
        addTags(bookmark);
        ctx.getResponse().status(HttpURLConnection.HTTP_CREATED);
        ctx.insert(App::freemarkerBookmarkList);
      }
    }
  }

  private static void freemarkerUpdateOrDeleteBookmark(Context ctx) throws Exception {
    Form form = ctx.parse(Form.class);
    String method = form.get("_method");
    if ("put".equals(method)) {
      freemarkerUpdateBookmark(ctx);
    }
    if ("delete".equals(method)) {
      freemarkerDeleteBookmark(ctx);
    } else {
      ctx.getResponse().status(HttpURLConnection.HTTP_BAD_REQUEST);
    }
  }

  private static void freemarkerDeleteBookmark(Context ctx) {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      long id = Long.parseLong(ctx.getPathTokens().get("id"));
      deleteTags(id);
      dao.deleteById(id);
      ctx.getResponse().status(HttpURLConnection.HTTP_OK);
      ctx.insert(App::freemarkerBookmarkList);
    }
  }

  private static void freemarkerUpdateBookmark(Context ctx) throws Exception {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      long id = Long.parseLong(ctx.getPathTokens().get("id"));
      Form form = ctx.parse(Form.class);
      String title = form.get("title");
      String url = form.get("url");
      String tags = form.get("tags");
      Bookmark bookmark = dao.findById(id);
      if (bookmark == null) {
        ctx.getResponse().status(HttpURLConnection.HTTP_NOT_FOUND);
        ctx.getResponse().send();
      } else {
        bookmark.setTitle(title);
        bookmark.setUrl(url);
        bookmark.setTags(tags);
        if (validateForUpdate(ctx, bookmark)) {
          dao.update(bookmark);
          addTags(bookmark);
          ctx.getResponse().status(HttpURLConnection.HTTP_OK);
          ctx.insert(App::freemarkerBookmarkList);
        }
      }
    }
  }

  private static class FreemarkerRenderer extends RendererSupport<FreemarkerModel> {
    @Override
    public void render(Context ctx, FreemarkerModel model) throws Exception {
      StringWriter stringWriter = new StringWriter();

      Template template = freemarkerConfig.getTemplate("index.ftl");
      template.process(model, stringWriter);

      ctx.getResponse().contentType(MediaType.TEXT_HTML);
      ctx.getResponse().send(stringWriter.toString());
    }
  }

  private static class FreemarkerModel extends HashMap<String, Object> {}

  static void addTags(Bookmark bookmark) {
    try (TagDAO tagDAO = dbi.open(TagDAO.class);
         TaggingDAO taggingDAO = dbi.open(TaggingDAO.class)) {
      String tags = bookmark.getTags() == null ? "" : bookmark.getTags();
      Set<String> newLabels = getTagSet(tags);
      List<Tag> currentTags = tagDAO.findByBookmarkId(bookmark.getId());
      List<String> toKeep = new ArrayList<>();
      List<Long> toDelete = new ArrayList<>();
      for (Tag tag : currentTags) {
        if (newLabels.contains(tag.getLabel())) {
          toKeep.add(tag.getLabel());
        } else {
          toDelete.add(tag.getId());
        }
      }
      for (Long tagId: toDelete) {
        taggingDAO.delete(bookmark.getId(), tagId);
        if (taggingDAO.countByTagId(tagId) < 1) {
          tagDAO.deleteById(tagId);
        }
      }
      newLabels.removeAll(toKeep);
      for (String label : newLabels) {
        Tag tag = tagDAO.findByLabel(label);
        if (tag == null) {
          tag = new Tag(label);
          tag.setId(tagDAO.insert(tag));
        }
        taggingDAO.insert(new Tagging(bookmark.getId(), tag.getId()));
      }
    }
  }

  private static Set<String> getTagSet(String tags) {
    List<String> inputLabels = new ArrayList<>(Arrays.asList(tags.split(",")));
    ListIterator<String> it = inputLabels.listIterator();
    while (it.hasNext()) {
      String element = it.next().trim();
      if (element.isEmpty()) {
        it.remove();
      } else {
        it.set(element);
      }
    }
    return new HashSet<>(inputLabels);
  }

  private static void deleteTags(long bookmarkId) {
    try (TagDAO tagDAO = dbi.open(TagDAO.class);
         TaggingDAO taggingDAO = dbi.open(TaggingDAO.class)) {
      List<Long> toDelete = taggingDAO.findTagIdByBookmarkId(bookmarkId);
      for (Long tagId : toDelete) {
        taggingDAO.delete(bookmarkId, tagId);
        if (taggingDAO.countByTagId(tagId) < 1) {
          tagDAO.deleteById(tagId);
        }
      }
    }
  }

}
