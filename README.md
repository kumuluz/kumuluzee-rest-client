# KumuluzEE MicroProfile Rest Client

> KumuluzEE MicroProfile Rest Client project provides an easy way to define and invoke RESTful services over HTTP.

KumuluzEE MicroProfile Rest Client supports generation of rest clients from simple definitions. APIs are defined using
interfaces and well-known JAX-RS annotations. Generated rest clients provide a type-safe way to invoke defined APIs
and support a wide variety of providers which allow fine-grained but natural configuration at various stages of
requests.

KumuluzEE MicroProfile Rest Client 1.0.1 implements
[MicroProfile Rest Client](https://microprofile.io/project/eclipse/microprofile-rest-client) 1.0.1 API.

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

#### Generating rest client programatically

Rest client can be built programmantically anywhere in application code.

```java
SimpleApi simpleApi = RestClientBuilder
    .newBuilder()
    .baseUrl(new URL("http://myapi.location.com"))
    .build(SimpleApi.class);
```

#### Generating rest client with CDI injection

Injection of rest client is supported in CDI beans and offers an approach of generating a rest client with less
boilerplate.

```java
@Inject
@RestClient
SimpleApi simpleApi;
```

If you are using CDI injection to create rest client, you must annotate your interface with `@RegisterRestClient` and
define the base URL using configuration parameter as described below.

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
```

The `class` property identifies the definition of rest client by its class name. The `url` property defines the base URL
for generated rest clients and the `providers` property is a comma separated list of providers, that are added to the
generated rest clients.

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
