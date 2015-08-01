$(document).ready(function() {
  // Use can for CanJS
  var $result = $("#result");
  can.each(["One", "Two", "Three"], function(it) {
    $result.append(it).append(", ");
  });
  $result.append("Go CanJS!");
});