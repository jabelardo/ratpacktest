package jags.ratpacktest;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

import java.util.List;

/**
 * Created by jose abelardo gutierrez on 7/27/15.
 */
public interface TaggingDAO extends AutoCloseable {

  @Override
  void close();

  @SqlUpdate("create table tagging("
      + "bookmark_id integer not null,"
      + "tag_id integer not null,"
      + "primary key(bookmark_id, tag_id))")
  void createTaggingTable();

  @SqlUpdate("insert into tagging(bookmark_id, tag_id) values(:bookmarkId, :tagId)")
  long insert(@BindBean Tagging tagging);

  @SqlUpdate("delete from tagging where bookmark_id = :bookmarkId and tag_id = :tagId")
  void delete(@Bind("bookmarkId") Long bookmarkId, @Bind("tagId") Long tagId);

  @SqlQuery("select count(*) from tagging where tag_id = :tagId")
  int countByTagId(@Bind("tagId") Long tagId);

  @SqlQuery("select tag_id from tagging where bookmark_id = :bookmarkId")
  List<Long> findTagIdByBookmarkId(@Bind("bookmarkId") Long  bookmarkId);

  @SqlQuery("select count(*) from tagging")
  int count();

  @SqlUpdate("delete from tagging")
  void delete();
}
