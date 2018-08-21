# KumuluzEE MicroProfile Rest Client

> KumuluzEE MicroProfile Rest Client extension provides you with a typesafe rest client.

KumuluzEE MicroProfile Rest Client 1.0.1 implements [MicroProfile Rest Client](https://microprofile.io/project/eclipse/microprofile-rest-client) 1.0.1 API.

## Usage

MicroProfile Rest Client can be added via the following Maven dependency:
```xml
<dependency>
    <groupId>com.kumuluz.ee.rest-client-mp</groupId>
    <artifactId>rest-client-mp</artifactId>
    <version>${rest-client.version}</version>
</dependency>
```

### Defining typesafe API

To define a typesafe API, we need to create an interface:

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

There are two ways to build rest client:

#### Programatically

```java
SimpleApi simpleApi = RestClientBuilder
    .newBuilder()
    .baseUrl(new URL("http://myapi.location.com"))
    .build(SimpleApi.class);
```

#### With CDI injection

```java
@Inject
@RestClient
SimpleApi simpleApi;
```

If you are using CDI injection to create rest client, you must annotate your interface with `@RegisterRestClient` and in your configuration put key `package.name.SimpleApi/mp-rest/url: http://myapi.location.com`

## Changelog

Recent changes can be viewed on Github on the [Releases Page]()

## Contribute

See the [contributing docs]()

When submitting an issue, please follow the 
[guidelines]().

When submitting a bugfix, write a test that exposes the bug and fails before applying your fix. Submit the test 
alongside the fix.

When submitting a new feature, add tests that cover the feature.

## License

MIT