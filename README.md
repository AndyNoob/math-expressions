# Math Expressions

[![](https://jitpack.io/v/AndyNoob/math-expressions.svg)](https://jitpack.io/#AndyNoob/math-expressions)<br>
[![discord banner](https://discordapp.com/api/guilds/1184300001405440030/widget.png?style=banner2)](https://discord.gg/hmqspPuhHd)

A lil one class library to quickly evaluate simple math expressions. Or just to parse it and apply syntax highlighting.

## How 2 Use

(just click the jitpack badge)

Use the `MathExpression#parse` method to get an instance of `MathExpression`. Think of this like compiling ahead of time. The required argument is the string of the expression (i.e. `19 * 12`). You may also provide a map of string (name of variable) and double (value of variable) as an optional second argument. Alternatively, you can set a variable by invoking `MathExpression#setVariable` (chainable method), which takes a string and double. All of the constants in the JDK `Math` class are added automatically as built-in variables (i.e. `PI`). Once you are ready, simply invoke `MathExpression#evaluate`. You may invoke the method multiple times (intended for if the variables change). 

<details>

<summary>Maven (edit pom.xml)</summary>

```xml
    <repositories>
        ...
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
        ...
    </repositories>
```

```xml
    <dependencies>
        ...
        <dependency>
            <groupId>com.github.AndyNoob</groupId>
            <artifactId>math-expressions</artifactId>
            <version>INSERT VERSION HERE!!!!!!</version>
        </dependency>
        ...
    </dependencies>
```

</details>

<details>

<summary>Gradle (edit build.gradle)</summary>

```groovy
repositories {
    // ...
    maven { url 'https://jitpack.io' }
    // ...
}
```

```groovy
dependencies {
    // ...
    implementation 'com.github.AndyNoob:math-expressions:INSERT VERSION HERE !!!'
    // ...
}
```

</details>
