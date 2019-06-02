package com.xatkit.plugins.slack;

import com.xatkit.core.XatkitCore;
import com.xatkit.core.session.RuntimeContexts;
import com.xatkit.plugins.chat.ChatUtils;
import com.xatkit.plugins.slack.platform.SlackPlatform;
import com.xatkit.plugins.slack.platform.io.SlackIntentProvider;
import org.apache.commons.configuration2.Configuration;

/**
 * An utility interface that holds Slack-related helpers.
 * <p>
 * This class defines the xatkit configuration key to store the Slack bot API token, as well as a set of API response
 * types that are used internally to check connection and filter incoming events.
 */
public interface SlackUtils extends ChatUtils {

    /**
     * The {@link Configuration} key to store the Slack bot API token.
     *
     * @see SlackIntentProvider#SlackIntentProvider(SlackPlatform, Configuration)
     * @see SlackPlatform#SlackPlatform(XatkitCore, Configuration)
     */
    String SLACK_TOKEN_KEY = "xatkit.slack.token";

    /**
     * The Slack API answer type representing a {@code message}.
     */
    String MESSAGE_TYPE = "message";

    /**
     * The Slack API answer type representing a successful authentication.
     */
    String HELLO_TYPE = "hello";

    /**
     * The {@link RuntimeContexts} key used to store slack-related information.
     */
    String SLACK_CONTEXT_KEY = "slack";

}
