package com.safjnest.util.lol;

import com.safjnest.sql.QueryRecord;

public class CustomBuildData extends BuildData {

    private String name;
    private String description;
    private String userId;

    public CustomBuildData(QueryRecord result) {
      super(result);
      this.name = result.get("name");
      this.description = result.get("description");
      this.userId = result.get("user_id");
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public String getUserId() {
      return userId;
    }
}
