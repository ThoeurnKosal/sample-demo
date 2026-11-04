package com.mysample.demo.customer.infrastructure.client.jsonplaceholder;

final class DirectoryCaches {

    /** User by external (directory) id. */
    static final String USERS = "directory-users";

    /** Posts of a user by external id. */
    static final String POSTS = "directory-posts";

    private DirectoryCaches() {
    }
}
