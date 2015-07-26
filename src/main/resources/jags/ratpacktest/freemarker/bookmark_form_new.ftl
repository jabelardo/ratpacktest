<#escape x as x?html>
  <h2>New Bookmark</h2>
  <form action="/freemarker/bookmarks" method="post">
    <#include "bookmark_form_inputs.ftl">
  </form>
</#escape>