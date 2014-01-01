package com.hp.oo.execution.reflection;

import com.hp.oo.internal.sdk.execution.ControlActionMetadata;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: kravtsov
 * Date: 09/11/11
 * Time: 11:49
 */
public interface ReflectionAdapter {
    public Object executeControlAction(ControlActionMetadata actionMetadata, Map<String, ?> actionData)  ;
}
