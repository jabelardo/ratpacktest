package jags.ratpacktest;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;

import java.util.List;

/**
 * Created by jose abelardo gutierrez on 7/25/15.
 */
public interface BookmarkDAO extends AutoCloseable {

  @SqlUpdate("create table bookmark("
      + "id identity primary key,"
      + "url varchar(2048) not null,"
      + "title varchar(80) not null,"
      + "creation_timestamp timestamp not null)")
  void createBookmarkTable();

  @SqlQuery("select id, url, title, creation_timestamp from bookmark where id = :id")
  @Mapper(Bookmark.Mapper.class)
  Bookmark findOne(@Bind("id") Long id);

  @SqlQuery("select id, url, title, creation_timestamp from bookmark order by title")
  @Mapper(Bookmark.Mapper.class)
  List<Bookmark> findOrderByTitle();

  @SqlQuery("select id, url, title, creation_timestamp from bookmark order by creation_timestamp")
  @Mapper(Bookmark.Mapper.class)
  List<Bookmark> findOrderByCreationTimestamp();

  @SqlUpdate("insert into bookmark(url, title, creation_timestamp) values(:url, :title, current_timestamp())")
  @GetGeneratedKeys
  long insert(@BindBean Bookmark bookmark);

  @SqlUpdate("update bookmark set url = :url, title = :title where id = :id")
  void update(@BindBean Bookmark bookmark);

  @SqlUpdate("delete from bookmark where id = :id")
  void delete(@Bind("id") Long id);

  @Override
  void close();
}
