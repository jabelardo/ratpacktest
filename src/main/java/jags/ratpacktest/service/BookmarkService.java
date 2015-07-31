package jags.ratpacktest.service;

import jags.ratpacktest.dao.BookmarkDAO;
import jags.ratpacktest.dao.TagDAO;
import jags.ratpacktest.dao.TaggingDAO;
import jags.ratpacktest.domain.Bookmark;
import jags.ratpacktest.domain.Tag;
import jags.ratpacktest.domain.Tagging;
import jags.ratpacktest.exception.ValidationException;
import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.util.StringUtils;
import org.skife.jdbi.v2.DBI;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.sql.DataSource;

/**
 * Created by jose abelardo gutierrez on 7/30/15.
 */
public class BookmarkService {

  private DataSource ds = JdbcConnectionPool.create("jdbc:h2:mem:test", "sa", "");
  private DBI dbi = new DBI(ds);

  public BookmarkService() {
    try (BookmarkDAO bookmarkDAO = dbi.open(BookmarkDAO.class);
        TagDAO tagDAO = dbi.open(TagDAO.class);
        TaggingDAO taggingDAO = dbi.open(TaggingDAO.class)) {
      bookmarkDAO.createBookmarkTable();
      tagDAO.createTagTable();
      taggingDAO.createTaggingTable();
    } catch (Exception ignored) {
    }
  }

  public void deleteBookmark(long id) {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      deleteTags(id);
      dao.deleteById(id);
    }
  }

  public Bookmark getBookmark(long id) {
    try (BookmarkDAO bookmarkDAO = dbi.open(BookmarkDAO.class)) {
      Bookmark bookmark = bookmarkDAO.findById(id);
      if (bookmark != null) {
        setTags(bookmark);
      }
      return bookmark;
    }
  }

  public Bookmark updateBookmark(Bookmark bookmark) throws ValidationException {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      validateForUpdate(bookmark);
      Bookmark existent = dao.findById(bookmark.getId());
      if (existent != null) {
        existent.setTitle(bookmark.getTitle());
        existent.setUrl(bookmark.getUrl());
        existent.setTags(bookmark.getTags());
        dao.update(existent);
        addTags(existent);
      }
      return existent;
    }
  }

  public List<Tag> getTags() {
    try (TagDAO dao = dbi.open(TagDAO.class)) {
      return dao.findOrderByLabel();
    }
  }

  public void deleteTags(long bookmarkId) {
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

  public void setTags(Bookmark bookmark) {
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

  public void addTags(Bookmark bookmark) {
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

  public static Set<String> getTagSet(String tags) {
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

  public List<Bookmark> getBookmarksOrderByTitle(String tagsStr, String order) {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      if (!StringUtils.isNullOrEmpty(order) && "creation_timestamp".equals(order)) {
        if (StringUtils.isNullOrEmpty(tagsStr)) {
          return dao.findOrderByCreationTimestamp();
        } else {
          Set<String> tags = getTagSet(tagsStr);
          return dao.findByTagLabelsOrderByCreationTimestamp(tags);
        }
      } else {
        if (StringUtils.isNullOrEmpty(tagsStr)) {
          return dao.findOrderByTitle();
        } else {
          Set<String> tags = getTagSet(tagsStr);
          return dao.findByTagLabelsOrderByTitle(tags);
        }
      }
    }
  }

  public Bookmark createBookmark(Bookmark bookmark) throws ValidationException {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      validateForCreate(bookmark);
      Long bookmarkId = dao.insert(bookmark);
      bookmark.setId(bookmarkId);
      addTags(bookmark);
      return bookmark;
    }
  }

  public static void validateForUpdate(Bookmark bookmark) throws ValidationException {
    if (StringUtils.isNullOrEmpty(bookmark.getTitle())) {
      throw new ValidationException("title can't be empty");
    }
    if (StringUtils.isNullOrEmpty(bookmark.getUrl())) {
      throw new ValidationException("url can't be empty");
    } else {
      try {
        new URL(bookmark.getUrl());
      } catch (MalformedURLException e) {
        throw new ValidationException("url is not valid");
      }
    }
  }

  public static void validateForCreate(Bookmark bookmark) throws ValidationException {
    if (bookmark.getId() != null) {
      throw new ValidationException("id must be null");
    }
    validateForUpdate(bookmark);
  }
}
