package com.mysample.demo.customer.domain.port;

import com.mysample.demo.customer.domain.CustomerPost;
import com.mysample.demo.customer.domain.ExternalUser;
import java.util.List;

public interface CustomerDirectoryPort {

    ExternalUser findUser(int externalId);

    List<CustomerPost> findPosts(int externalId);

}
