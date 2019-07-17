# KumuluzEE MicroProfile Rest Client
[![Build Status](https://travis-ci.org/kumuluz/kumuluzee-rest-client.svg?branch=master)](https://travis-ci.org/kumuluz/kumuluzee-rest-client)

> KumuluzEE MicroProfile Rest Client project provides an easy way to define and invoke RESTful services over HTTP.

KumuluzEE MicroProfile Rest Client supports generation of rest clients from simple definitions. APIs are defined using
interfaces and well-known JAX-RS annotations. Generated rest clients provide a type-safe way to invoke defined APIs
and support a wide variety of providers which allow fine-grained but natural configuration at various stages of
requests.

KumuluzEE MicroProfile Rest Client implements
[MicroProfile Rest Client](https://microprofile.io/project/eclipse/microprofile-rest-client) 1.3.3 API.

## Usage

MicroProfile Rest Client can be added via the following Maven dependency:

```xml
<dependency>
    <groupId>com.kumuluz.ee.rest-client</groupId>
    <artifactId>kumuluzee-rest-client</artifactId>
    <version>${kumuluzee-rest-client.version}</version>
</dependency>
```

### Defining type-safe API

A type-safe API is defined by creating an interface:

```java
@Path("orders")
@RegisterRestClient
@Dependent
public interface SimpleApi {
	
	@GET
	@Path("{id}")
	Order getById(@PathParam("id") long id);
	
}
```

### Building rest client instance

Defined rest client can be generated programmatically or injected using CDI.

#### Generating rest client programmatically

Rest client can be built programmatically anywhere in application code.

```java
SimpleApi simpleApi = RestClientBuilder
    .newBuilder()
    .baseUrl(new URL("http://myapi.location.com"))
    .build(SimpleApi.class);
```

All constructed rest clients implement the `Closeable` and `AutoCloseable` interfaces and can thus be manually closed.

#### Generating rest client with CDI injection

Injection of rest client is supported in CDI beans and offers an approach of generating a rest client with less
boilerplate.

```java
@Inject
@RestClient
SimpleApi simpleApi;
```

If you are using CDI injection to create rest client, you must annotate your interface with `@RegisterRestClient`. The
URL of the api can be supplied with the annotation parameter or using configuration parameter as described below.

### Using providers

KumuluzEE Rest Client supports the usage of additional providers, which enable fine-grained control of requests at
various stages. Providers are implemented in similar fashion as in JAX-RS specification.

The following providers are supported:

- `ClientRequestFilter` - invoked when a request is made.
- `ClientResponseFilter` - invoked when a response is received.
- `MessageBodyReader` - allows reading the entity from the response.
- `MessageBodyWriter` - allows writing the entity to the request.
- `ParamConverter` - allows conversion of request/response parameters to and from `String`.
- `ReaderInterceptor` and `WriterInterceptor` - listeners for when a read/write occurs.
- `ResponseExceptionMapper` - maps received `Response` to a `Throwable` that is thrown by runtime.
- `AsyncInvocationInterceptorFactory` - creates interceptor for manipulating the thread in which asynchronous calls are
executed

#### Provider registration

Providers can be registered either programmatically when building the rest client or with annotations placed on the
definition class.

Example of programmatic registration:

```java
SimpleApi simpleApi = RestClientBuilder
    .newBuilder()
    .baseUrl(new URL("http://myapi.location.com"))
    .register(MyProvider.class)
    .build(SimpleApi.class);
```

Example of registration with annotations:

```java
@Path("orders")
@RegisterRestClient
@RegisterProvider(MyProvider.class)
@Dependent
public interface SimpleApi {
	
	@GET
	@Path("{id}")
	Order getById(@PathParam("id") long id);
	
}
```

### Configuration of rest client definitions

Rest client definitions can be additionally configured using the KumuluzEE configuration. Example configuration for the
`cdi.api.TodoApi`:

```yaml
kumuluzee:
  rest-client:
    registrations:
      - class: cdi.api.TodoApi
        url: https://jsonplaceholder.typicode.com
        providers: com.example.providers.Provider1,com.example.providers.Provider2
        connect-timeout: 1000
        read-timeout: 5000
```

The `class` property identifies the definition of rest client by its class name. The `url` property defines the base URL
for generated rest clients and the `providers` property is a comma separated list of providers, that are added to the
generated rest clients.

Additionally the following configuration keys are supported:

- `connect-timeout` - Connection timeout in milliseconds.
- `read-timeout` - Read timeout in milliseconds.
- `scope` - Fully qualified class name of the desired scope of the rest client.
- `hostname-verifier` - Fully qualified class name of the desired implementation of `HostnameVerifier`.
- `key-store` - Location of the client key store. Can point to either a classpath resource (e.g. `classpath:/my-keystore.jks`) or a file (e.g. `file:/home/user/my-keystore.jks`).
- `key-store-type` - Type of the client key store (`JKS` by default).
- `key-store-password` - Password of the client key store.
- `trust-store` - Location of the trust store. Can point to either a classpath resource (e.g. `classpath:/my-truststore.jks`) or a file (e.g. `file:/home/user/my-truststore.jks`).
- `trust-store-type` - Type of the trust store (`JKS` by default).
- `trust-store-password` - Password of the trust store.

Instead of using fully qualified class names for the configuration a configuration keys can also be used. This is
especially useful when multiple client definitions share the same configuration. For example for the following
definition:

```java
@RegisterRestClient(configKey="test-client")
public interface TestClient {
  @GET
  Response test();
}
```

The following configuration can be used (notice that the `class` key matches the `configKey` parameter of the
`@RegisterRestClient` annotation):

```yaml
kumuluzee:
  rest-client:
    registrations:
      - class: test-client
        url: https://my-test-service
        read-timeout: 5000
```

When using both configuration keys and fully qualified class names for the configuration the fully qualified class
name configuration takes precedence.

### Making asynchronous requests

In order to make requests asynchronously the method in the API interface should return parameterized type
`CompletionStage`. For example:

```java
@POST
CompletionStage<Void> createCustomerAsynch(Customer customer);
```

The defined method can then be used to make asynchronous requests. For example:

```java
customerApi.createCustomerAsynch(c)
    .toCompletableFuture().get();
```

### Adding headers on the API interface

Headers can be added to request in multiple ways. You can use the JAX-RS `@HeaderParam` parameter annotation. For
example:

```java
@GET
List<User> getUsers(@HeaderParam("Authorization") String authorization);
```

If using a parameter for header creation is not desirable using `@ClientHeaderParam` annotation on API interface or
method is also supported. For example:

```java
@POST
@ClientHeaderParam(name="X-Http-Method-Override", value="PUT")
Response sentPUTviaPOST(MyEntity entity);
```

The value of the `@ClientHeaderParam` can also use a method reference to generate header values. For example:

```java
@Path("/somePath")
public interface MyClient {
  @POST
  @ClientHeaderParam(name="X-Request-ID", value="{generateRequestId}")
  Response postWithRequestId(MyEntity entity);
  
  @GET
  @ClientHeaderParam(name="CustomHeader", value="{some.pkg.MyHeaderGenerator.generateCustomHeader}", required=false)
  Response getWithoutCustomHeader();
  
  default String generateRequestId() {
    return UUID.randomUUID().toString();
  }
}
```

The method reference can point to a default method defined in the API or a public static method in a different class.
If `required` parameter is set to `true` and the generator method throws an exception, the request will fail. If
`required` parameter is set to `false` and the generator method throws an exception, the request will not fail but
(unsuccessfully) generated header will not be sent. Default value is `true`.

Generator method must have zero arguments or one `String` argument (name of the header) and should return a `String`
representing the header value or `String[]` representing multiple header values.

### Propagating headers

KumuluzEE Rest Client supports propagation of headers from incoming requests to the outgoing requests. To enable this
feature annotate the API interface with the `@RegisterClientHeaders` annotation. Then specify the headers that should be
propagated in the configuration. For example to forward the _Authorization_ header put the following configuration in
_config.yaml_:

```yaml
kumuluzee:
  rest-client:
    propagate-headers: Authorization
```

__NOTE:__ Header propagation requires the [KumuluzEE Config MicroProfile](https://github.com/kumuluz/kumuluzee-config-mp)
dependency!

If more control is needed, an implementation of `ClientHeadersFactory` can be used by registering it in the
`@RegisterClientHeaders` parameter. Example implementation:

```java
public class ExampleHeadersFactory implements ClientHeadersFactory {
    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
                                                 MultivaluedMap<String, String> clientOutgoingHeaders) {
        // incomingHeaders - headers from the JAX-RS request
        // clientOutgoingHeaders - headers specified on the API interface
        // should return a map of headers which should actually be sent when making a request
    }
}
```

Note that this disables the default propagation, header propagation should be handled manually in the factory
implementation. This approach also doesn't require the KumuluzEE Config MicroProfile dependency.

### Intercepting new client builders

When a new client is being built it can be intercepted with a SPI interface `RestClientBuilderListener`. This includes
the builders that are created in the CDI environment at the start of the application (interfaces annotated with
`@RegisterRestClient`). For example:

```java
public class BuilderListener implements RestClientBuilderListener {

    @Override
    public void onNewBuilder(RestClientBuilder builder) {
        // ...
    }
}
```

Remember to register the listener in the service file named
`org.eclipse.microprofile.rest.client.spi.RestClientBuilderListener`.

A similar interface supported since 1.2.0 is the `RestClientListener` interface. The difference between the two is that
the `RestClientBuilderListener` implementations are called when a new builder is created and the `RestClientListener`
implementations are called when the _build_ method is called on the builder. The latter also exposes the service
interface class.

## Changelog

Recent changes can be viewed on Github on the [Releases Page](https://github.com/kumuluz/kumuluzee-rest-client/releases)

## Contribute

See the [contributing docs](https://github.com/kumuluz/kumuluzee-rest-client/blob/master/CONTRIBUTING.md)

When submitting an issue, please follow the 
[guidelines](https://github.com/kumuluz/kumuluzee-rest-client/blob/master/CONTRIBUTING.md#bugs).

When submitting a bugfix, write a test that exposes the bug and fails before applying your fix. Submit the test 
alongside the fix.

When submitting a new feature, add tests that cover the feature.

## License

MIT
