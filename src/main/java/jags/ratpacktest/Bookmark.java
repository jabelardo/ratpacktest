package jags.ratpacktest;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

/**
 * Created by jose abelardo gutierrez on 7/25/15.
 */
public class Bookmark {
  private Long id;
  private String url;
  private String title;
  private Date creationTimestamp;
  private String tags;

  public Bookmark(Long id, String title, String url) {
    this.id = id;
    this.title = title;
    this.url = url;
  }

  public Bookmark(String title, String url) {
    this.title = title;
    this.url = url;
  }

  public Bookmark() {
  }

  public Bookmark(long id, String title, String url, Date creationTimestamp) {
    this.id = id;
    this.title = title;
    this.url = url;
    this.creationTimestamp = creationTimestamp;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getUrl() {
    return url;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Date getCreationTimestamp() {
    return creationTimestamp;
  }

  public String getTags() {
    return tags;
  }

  public void setTags(String tags) {
    this.tags = tags;
  }

  public static class Mapper implements ResultSetMapper<Bookmark> {
    @Override
    public Bookmark map(int idx, ResultSet rs, StatementContext sc)
        throws SQLException {
      return new Bookmark(rs.getLong("id"), rs.getString("title"), rs.getString("url"), rs.getDate("creation_timestamp"));
    }
  }
}
