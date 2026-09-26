# xjc-bean-validation-plugin-example

An example of [`xjc-bean-validation-plugin`](https://github.com/fillumina/xjc-bean-validation-plugin)
inside a real build, and the functional test of that wiring. The `jaxb-maven-plugin` runs the plugin
over `src/main/xsd/invoice.xsd`, the generated sources are compiled by the same build, and a test
reads the annotations that came out and has Hibernate Validator enforce them.

```xml
<plugin>
  <groupId>org.jvnet.jaxb</groupId>
  <artifactId>jaxb-maven-plugin</artifactId>
  <version>4.0.9</version>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <extension>true</extension>
    <schemaDirectory>${project.basedir}/src/main/xsd</schemaDirectory>
    <args>
      <arg>-p</arg>
      <arg>com.example.invoice</arg>
      <arg>-XBeanValidationAnnotations</arg>
      <arg>-XBeanValidationAnnotations:omitJavaTypeBounds=true</arg>
      <arg>-XBeanValidationAnnotations:override=*#note@Size:message = "at most 200 characters"</arg>
    </args>
    <plugins>
      <plugin>
        <groupId>com.fillumina</groupId>
        <artifactId>xjc-bean-validation-plugin</artifactId>
        <version>${xjc-bean-validation-plugin.version}</version>
      </plugin>
    </plugins>
  </configuration>
</plugin>
```

Both arguments are needed: the bare option activates the plugin, the one carrying a value
configures it. The `override` statement in this build changes the message of the length constraint
of the `note` element, which the test checks, so the option is exercised and not only shown.

## What the schema makes the plugin write

`invoice.xsd` is a schema of the size a real one has: a required invoice number with a pattern, a
date, a nested customer with its own address, a repeating line item with numeric bounds, optional
elements, a pinned boolean, an `xs:list` of tags, a choice, and two attributes, one of them
required.

| in the schema | on the generated field |
| --- | --- |
| a required element | `@NotNull`, and `@Valid` when its type is a complex one |
| an optional element | nothing, because being absent is allowed |
| `minLength`, `maxLength` | `@Size(min = …, max = …)` |
| `minInclusive`, `maxInclusive` | `@DecimalMin`, `@DecimalMax` |
| `totalDigits`, `fractionDigits` | `@Digits(integer = …, fraction = …)` |
| `pattern` | `@Pattern(regexp = "…")`, translated from the XSD dialect to Java |
| a boolean with `fixed` | `@AssertTrue` or `@AssertFalse` |
| a required attribute | `@NotNull`, exactly as an element |
| an element repeating up to n times | `@Size(max = n)` on the field, and `@Valid` on the item |
| the item type of an `xs:list` | its facets on the type argument, as `List<@Size(min = 2, max = 20) String>` |

## Building

The build needs JDK 21 and Maven, and nothing else:

```
mvn -B verify -Dxjc-bean-validation-plugin.version=<version>
```

The version is a property, so the example runs against a release from Maven Central and against a
snapshot without being touched. The snapshot side needs `mvn install` in the project itself first:

```
cd ../xjc-bean-validation-plugin && mvn -B install
cd ../xjc-bean-validation-plugin-example && mvn -B verify -Dxjc-bean-validation-plugin.version=1.0.0-SNAPSHOT
```

## What the test proves, and what it does not

`TheAnnotationsAreWrittenAndEnforcedTest` reads the annotations from the compiled generated classes
and validates invoices with Hibernate Validator: an invoice that breaks no rule passes, one with a
malformed number, too many items in a line, a pinned boolean set to `true` and too many digits in
the total reports those violations, and a broken address is reported through the cascade as
`customer.address.zip`.

It is the consumer path — the plugin reached through the codegen plugin of a real build — and not a
replacement for the fixture suite of the project, which is where the behaviour of the plugin itself
is pinned.

The three plugins of this line together in one build, which is where the split is shown to do what
the single plugin did, are in
[`xjc-plugins-example`](https://github.com/fillumina/xjc-plugins-example).
