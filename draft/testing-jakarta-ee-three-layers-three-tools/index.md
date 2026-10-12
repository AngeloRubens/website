---
title: "Testing Jakarta EE: Three Layers, Three Tools"
date: "2026-01-01"
description: "A guide to Simon Laden's three-part series on testing Jakarta EE with WeldInitiator, Arquillian and Testcontainers, and the question each tool answers."
authors:
  - "dominika-tasarz"
image: "cover.jpg"
categories:
  - "Jakarta EE"
  - "Testing"
  - "Testcontainers"
related_posts: []
---

Testing a Jakarta EE application is awkward because the behaviour you care about often doesn't exist until a container assembles it. Mock everything and your tests pass while proving very little. Deploy everything and you get slow scenarios nobody runs during development.

A recent three-part series by Simon Laden, *Testing Jakarta EE: Three Layers, Three Tools*, shows how WeldInitiator, Arquillian and Testcontainers each answer a different question. All three parts run on the same example project, [testcontainers-example](https://github.com/payara/Payara-Examples/tree/main/ecosystem/testcontainers-example), a small library management system running on Azul Payara Micro. Here is a quick guide to the series.

## Part 1: WeldInitiator for CDI unit tests

*Does my bean's wiring and logic work in isolation?*

[CDI Unit Testing Without an Application Server: WeldInitiator in Practice](https://www.azul.com/blog/cdi-unit-testing-without-an-application-server-weldinitiator-in-practice/) shows how WeldInitiator boots a real Weld SE container inside your test JVM in milliseconds, with no server and no Docker. The post is just as clear about what it doesn't do: `@Transactional` and `@PersistenceContext` aren't honoured in a plain Weld SE test, and that changes what your assertions are worth.

## Part 2: Testcontainers for the deployed application

*Does the deployed application actually work?*

[Testcontainers Integration Testing: Running Your Real WAR in Docker](https://www.azul.com/blog/testcontainers-integration-testing-running-your-real-war-in-docker/) runs your Maven-built WAR on a real Azul Payara Micro instance, then tests it over HTTP and with Playwright in a headless browser. It covers the singleton container pattern and the startup race that makes naive versions of these tests flaky.

## Part 3: Choosing between the three tools

*Which tool for which job?*

[Jakarta EE Testing Tools Compared: WeldInitiator, Arquillian, and Testcontainers](https://www.azul.com/blog/jakarta-ee-testing-tools-compared-weldinitiator-arquillian-and-testcontainers/) puts all three side by side and adds Arquillian, which runs tests inside the container's own JVM. It shows why a mocked `EntityManager` can pass while the real query is broken, and ends with a comparison table you can take into a test-strategy discussion.

## The takeaway

These tools are layers, not alternatives: a passing test against a mock proves your code matches your assumptions, while only a real container proves the assumptions match reality.

Start with whichever part matches the problem you have today, or read all three in order.
