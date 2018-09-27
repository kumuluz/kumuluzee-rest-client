/*
 *  Copyright (c) 2014-2017 Kumuluz and/or its affiliates
 *  and other contributors as indicated by the @author tags and
 *  the contributor list.
 *
 *  Licensed under the MIT License (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://opensource.org/licenses/MIT
 *
 *  The software is provided "AS IS", WITHOUT WARRANTY OF ANY KIND, express or
 *  implied, including but not limited to the warranties of merchantability,
 *  fitness for a particular purpose and noninfringement. in no event shall the
 *  authors or copyright holders be liable for any claim, damages or other
 *  liability, whether in an action of contract, tort or otherwise, arising from,
 *  out of or in connection with the software or the use or other dealings in the
 *  software. See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.kumuluz.ee.rest.client.mp.util;

import org.eclipse.microprofile.rest.client.RestClientDefinitionException;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Validator utility for interfaces annotated with
 * {@link org.eclipse.microprofile.rest.client.inject.RegisterRestClient}.
 *
 * @author Miha Jamsek
 * @since 1.0.1
 */
public class InterfaceValidatorUtil {

    private static final String URI_PARAM_NAME_REGEX = "\\w[\\w.-]*";
    private static final String URI_PARAM_REGEX_REGEX = "[^{}][^{}]*";
    private static final String URI_REGEX_PATTERN = "\\{\\s*(" + URI_PARAM_NAME_REGEX + ")\\s*(:\\s*(" + URI_PARAM_REGEX_REGEX + "))?}";
    private static final Pattern URL_PARAM_PATTERN = Pattern.compile(URI_REGEX_PATTERN);
    private static final char openCurlyReplacement = 6;
    private static final char closeCurlyReplacement = 7;

    public static <T> void validateApiInterface(Class<T> apiClass) {
        checkForMultipleHttpMethods(apiClass.getMethods());
        checkForMatchingParams(apiClass);
    }

    private static <T> void checkForMatchingParams(Class<T> apiClass) {

        // declared variables
        Set<String> interfaceVariables = new HashSet<>();
        Set<String> methodsVariables = new HashSet<>();
        // assigned variables
        Set<String> methodParameterVariables = new HashSet<>();

        // get interface params
        Path interfacePathAnnotation = apiClass.getAnnotation(Path.class);
        if (interfacePathAnnotation != null) {
            interfaceVariables.addAll(getPathParamList(interfacePathAnnotation.value()));
        }

        // get methods params
        for (Method method : apiClass.getMethods()) {
            // add method annotation variables
            Path methodPathAnnotation = method.getAnnotation(Path.class);
            if (methodPathAnnotation != null) {
                methodsVariables.addAll(getPathParamList(methodPathAnnotation.value()));
            }
            // add method parameter variables
            for (Annotation[] annotations : method.getParameterAnnotations()) {
                for (Annotation annotation : annotations) {
                    if (PathParam.class.equals(annotation.annotationType())) {
                        methodParameterVariables.add(((PathParam) annotation).value());
                    }
                }
            }
        }

        Set<String> allDeclaredVariables = Stream
                .concat(interfaceVariables.stream(), methodsVariables.stream())
                .collect(Collectors.toSet());

        if (allDeclaredVariables.size() != methodParameterVariables.size()) {
            String message = String.format("Number of path parameters and variables don't match! Cause: %s", apiClass);
            throw new RestClientDefinitionException(message);
        }

        Set<String> unmappedPathParams = symmetricDifferenceOnSets(allDeclaredVariables, methodParameterVariables);
        if (unmappedPathParams.size() > 0) {
            String message = String.format("Number of path parameters and variables don't match! Cause: %s", apiClass);
            throw new RestClientDefinitionException(message);
        }
    }

    private static void checkForMultipleHttpMethods(Method[] methods) {
        for (Method method : methods) {
            boolean alreadyHasHTTPMethod = false;
            Annotation alreadyDefinedHttpMethod = null;
            for (Annotation annotation : method.getAnnotations()) {
                boolean annotationIsHTTPMethod = annotation.annotationType().getAnnotation(HttpMethod.class) != null;
                if (!alreadyHasHTTPMethod && annotationIsHTTPMethod) {
                    alreadyHasHTTPMethod = true;
                    alreadyDefinedHttpMethod = annotation;
                } else if (alreadyHasHTTPMethod && annotationIsHTTPMethod) {
                    String message = String.format(
                            "Multiple HTTP methods are not allowed! Cause: %s and %s on method %s!",
                            annotation, alreadyDefinedHttpMethod, method.getName());
                    throw new RestClientDefinitionException(message);
                }
            }
        }
    }

    private static List<String> getPathParamList(String string) {
        List<String> params = new ArrayList<>();
        Matcher matcher = URL_PARAM_PATTERN.matcher(replaceCurlyBraces(string));
        while (matcher.find()) {
            String param = matcher.group(1);
            params.add(param);
        }
        return params;
    }

    private static String replaceCurlyBraces(String string) {
        char[] chars = string.toCharArray();
        int open = 0;
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '{') {
                if (open != 0) chars[i] = openCurlyReplacement;
                open++;
            } else if (chars[i] == '}') {
                open--;
                if (open != 0) {
                    chars[i] = closeCurlyReplacement;
                }
            }
        }
        return new String(chars);
    }

    private static Set<String> symmetricDifferenceOnSets(Set<String> set1, Set<String> set2) {
        Set<String> differenceResult = new HashSet<>(set1);
        for (String elem : set2) {
            if (!differenceResult.add(elem)) {
                differenceResult.remove(elem);
            }
        }
        return differenceResult;
    }

}
