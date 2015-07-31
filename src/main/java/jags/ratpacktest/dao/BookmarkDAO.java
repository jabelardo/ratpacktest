package jags.ratpacktest.dao;

import jags.ratpacktest.domain.Bookmark;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseStringTemplate3StatementLocator;
import org.skife.jdbi.v2.unstable.BindIn;

import java.util.Collection;
import java.util.List;

/**
 * Created by jose abelardo gutierrez on 7/25/15.
 */
@UseStringTemplate3StatementLocator
public interface BookmarkDAO extends AutoCloseable {

  @Override
  void close();

  @SqlUpdate("create table bookmark("
      + "id identity primary key,"
      + "url varchar(2048) not null,"
      + "title varchar(80) not null,"
      + "creation_timestamp timestamp not null)")
  void createBookmarkTable();

  @SqlQuery("select id, url, title, creation_timestamp from bookmark where id = :id")
  @Mapper(Bookmark.Mapper.class)
  Bookmark findById(@Bind("id") Long id);

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
  void deleteById(@Bind("id") Long id);

  @SqlQuery("select distinct b.id id, b.url url, b.title title, b.creation_timestamp creation_timestamp "
      + "from bookmark b "
      + "join tagging on(b.id = tagging.bookmark_id) "
      + "join tag t on(t.id = tagging.tag_id) "
      + "where t.label in(<labels>) "
      + "order by b.title")
  @Mapper(Bookmark.Mapper.class)
  List<Bookmark> findByTagLabelsOrderByTitle(@BindIn("labels") Collection<String> labels);

  @SqlQuery("select distinct b.id id, b.url url, b.title title, b.creation_timestamp creation_timestamp "
      + "from bookmark b "
      + "join tagging on(b.id = tagging.bookmark_id) "
      + "join tag t on(t.id = tagging.tag_id) "
      + "where t.label in(<labels>) "
      + "order by b.creation_timestamp")
  @Mapper(Bookmark.Mapper.class)
  List<Bookmark> findByTagLabelsOrderByCreationTimestamp(@BindIn("labels") Collection<String> labels);

  @SqlQuery("select count(*) from bookmark")
  int count();

  @SqlUpdate("delete from bookmark")
  void delete();
}
