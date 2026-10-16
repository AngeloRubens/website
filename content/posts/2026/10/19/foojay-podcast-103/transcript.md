**[0:00]** Two very different Java projects in this podcast. One backend data layer and one crossplatform UI framework. Let's find out what they actually have in common.

**[0:10]** Welcome to the Foojay podcast. All your news about OpenJDK.

**[0:17]** Welcome back to the Foojay podcast. Runtime reflection has always been one of those quiet taxes on the GVM. It's flexible. It's everywhere. and it's very easy to stop noticing it's there until you go looking for where your CPU is actually going. Today's episode is about two teams that went looking in completely different corners of the ecosystem and landed on the same answer, push the work to build time. I have two guests on this podcast and I invite them both because of articles they have published on Foojay.io. Dimmitri Moscaluk is a new author on Foojay and recently published reflectionfree serialization on the GVM four formats in Avro out. In the article he explains a model layer that ingests JSON XML JAML and TOML and converts it all to Avro and the choice between Jackson and Cotlin X serialization that came down to exactly where the reflection cost hides and where it doesn't. Shai Almour has been posting on Foojay since September 2021. I went back to your first post, Chai. And the counter is now at 140 articles. He works on a built time code generation

**[1:33]** Pipeline for code name one that generates typed Java for open API clients, OM, JSON, XML mapping and more. And the goal is no reflection, no service loader, and no class for name. Dimmitri Shai, welcome to the podcast. Deitri, special welcome to you as a new Foojay author. I don't know if it's related to the new Foojay system that we have since we moved away from WordPress. did you discover us because it's now easier to contribute content with G via GitHub request?

**[2:07]** Yeah. Hi. thanks for having me. It's a pleasure to be here. And to answer your question, I think I've submitted my article just before the changes took the effect. So yeah, but maybe I will just publish a bit more articles now. [laughter]

**[2:26]** Yeah, that that's the hope of the system that it's now a bit easier. Shai, you are one of the regular authors. do you like the new approach?

**[2:34]** I love it. It's a huge improvement and we changed our own website in that way. I think actually going back to the question of reflection, the static generation is exactly that. It's a better approach. We already know when we're building the site what how we want it to look and spending the runtime cost on that is, you know, so it's the same sort of thing.

**[3:03]** Yeah, it's a it's a nice comparison indeed. We took away the work of WordPress loading content from a database and rendering it whenever someone did a page refresh to build time. Yeah, a nice similarity with the articles. So, let's go to the articles. Dimitri, let's start with your article. it's about Avo. I didn't know about it. It was something new for me. Can you explain what this AO thing is? so basically it's just a format for Kafka to digest message and to compress them. yeah so this format is about the schema so the data could be compressed efficiently.

**[3:49]** Mhm. is it comparable to like protobuffer? That's something I know.

**[3:54]** Yeah. Yeah. It's a layer upon that.

**[3:58]** Okay. Yeah. You compared it in the article with Jackson? We will dive into the details a bit later, but is there a big difference with Jason and Jason parsing?

**[4:09]** I thought that it would be a difference, but it turned out that it's not that much and sometimes Jackson wins and sometimes cotlin wins.

**[4:19]** Mhm. Okay. It depends like it's the most important and most returning answer in these questions. shy I only picked one article now which was about it was OM JSON mapping SVGs Lotty files because it also touches the same topic that Dimmitri was mentioning can you explain in two sentences code name one what is it [snorts]

**[4:47]** No [laughter]

**[4:50]** Okay you get three

**[4:51]** It's growing and expansive I just can't explain it in two sentences Sorry. Perame one is essentially a full stack. It's not just client anymore. We now also have a backend implementation. And the idea is to essentially do what Java always promised, a right once run anyway. That means we're more portable than Flutter in the sense that we reach every device but also backend now we use the same sort of VM that we built for mobile devices because servers have completely changed.

**[5:29]** Mhm. And one of our later articles in Foojay is about the fact that we with this same technology that works for iOS for devices like that have very constrained RAM we can give performance that's equivalent to go in terms of memory footprint response time things like that faster than grow VM in terms of startup and in terms of size obviously

**[6:00]** And faster in terms of compilation as well in a completely contained small executable and to do that you need to sort of remove reflection because you need to be able to shake the tree and get get to what's actually important in the app.

**[6:20]** Okay. now if you go back to the articles yeah so for code name one there are a lot of articles on Foojay people can really dive in and spend a few hours to learn on everything you've written. Now if you go back to your article Dimmitri it also mentions something about combining Jackson with Blackbird. That's again something new I learned from you. Can you explain what this blackird is doing? Yeah. So basically Blackboard is an internal mechanism in Jackson that u caches reflection API and also it generates by code to parse and to store data on the fly. And then if we go into the article in the afro that you compared what is a bit the problem that you discovered with reflection why is it not a good idea in thing you were doing

**[7:16]** I'd say that explicit is always better than implicit right

**[7:21]** So that's where I started at in this choice so yeah my first take was that no reflection is always better if it will work work faster but the truth turned out to be not quite that.

**[7:44]** Mhm. Mhm. What was the problem? Because yeah you compared all different formats you went from JSON XML jaml toml all to this a format.

**[7:53]** Yeah. Yeah. So the idea is basically to use codlin x mechanisms to generate parsers for all the input formats and simplify the job right yeah and doing that with Jackson it seemed a bit slower a bit sluggish and cotlin x was new and shiny for the team so here. we decided to give it a go.

**[8:27]** Mhm. And the results are not as you expected.

**[8:31]** Yeah. Yep. Exactly. So what it boils down to is that Codlin X missed a critical feature for the data parsing flow and we had to switch back to Jackson.

**[8:45]** Mhm.

**[8:46]** For a time at least.

**[8:48]** Mhm. What was the feature that you were missing? it was about keeping unknown fields, fields that are not in the schema.

**[8:58]** Okay. So you have Jackson JSON data. It has some unknown fields and if you export it back to JSON, you want to have those values again there.

**[9:07]** Yeah, we wanted to keep them. It was important for the business flow that we were trying to do.

**[9:15]** How do you solve that,

**[9:17]** Jackson? It has special callback for the unknown fields and you can basically pack them into some map like structure.

**[9:25]** Mhm. So this avo I went through the documentation very briefly before we started this recording. So it's you have to predefine again your structure a bit like the protobuff thing.

**[9:39]** Yeah. Exactly like this.

**[9:41]** Yeah. Yeah.

**[9:42]** Yeah. And it's generates Java code out of this or how does this convert into Java code?

**[9:50]** I think it generates some classes to basically create those objects. It's a part of a library.

**[10:01]** Yeah. If we go to your work Shai, so your baseline is no reflection, no service loader, no class for name. Why is this the thing you're working on?

**[10:15]** When we started, we needed to build a version of Java that's efficient. And back at Sun Micros Systemystems obviously we worked on J2ME back then on CDC as well on CLC which didn't include these features and the difference in terms of performance in terms of size in terms of the systems that we could get into was stark. And the thing is, people use these tools and you're thinking, why is my production server rediscovering what my compiler already knows? That's a huge huge waste. And even if the JIT can optimize it and it can't optimize everything and it needs to guard itself against deoptimizations, you still pay for that. You pay for that in startup. you pay for that and everything. Now that thing went into Java originally for Java beans for things like that and it became sort of the moment you have a feature you use it like the old joke that how much memory does your app need how much memory do you have that's sort of the same thing so the moment we have reflection people use it the moment we have class load class loader people use it sort of as a poor man's isolate and it's not good as

**[11:42]** An isolate But people still use it and still love it. the thing is that it's used as a sort of workaround for processes like server processes that run for years sometimes and get redeployed live. The modern servers use Kubernetes and deployments and lambdas and stuff like that. They need to load quickly and come down and they have all these guards externally that now we can't really use because our servers are too wasteful. The solution is to build without these things and build more modern things that can shrink to the level of go and tools like that. It doesn't mean that these tools are bad. If you have a system that needs the reflection for instance I'm from the field of observability as well. So here we can't build anything without reflection and class loading and everything. We need all of that and that's great if you need that but most systems I run into really don't need that. You have a production deployment and a staging deployment. And doing reflection just to toggle these two flags is kind of insane.

**[13:01]** And the moment we think in these terms and we had to think that way for mobile, it also makes sense for the back end. For instance, our server is grow VM and it's an amazing tool in some regards, but I spend 30 minutes on CI and pay for that CI mostly because grow VM VM is constrained by what Java expects it to be to provide reflection to provide all of that these things and it also generates a really large binary because of that because everything downstream in spring and everything expects that we built a server that essentially discards all of that while still providing a string like interface. So you still get the dependency injection, you still get JPA sort of like API. It's not JPA because we're not really Java. We don't have reflection.

**[14:00]** We don't have class loading. You write it in Java, but we can't call it Java. But it gives you the type of performance that you would expect from a language like Go while still being giving you the experience that you would expect from Java. And that's sort of the price. So reflection isn't the entire price here, but it's definitely part of the bill that you end up paying.

**[14:24]** Mhm. How are you feeling about generated automatically generated code like you had project lumbok where you could with some annotations the moment your project gets built code is injected into the system a lot of developers don't like that approach

**[14:44]** Nowadays everything is AI generated so we're bit back at that how do you feel about this generated code like you could apparently have with Avo or with other to tools like Lombok. Is that still a bad thing?

**[15:00]** I'd like to mention by the way Manifold, which goes even further than Lombok and also a pretty amazing project. Well, if you work with JPA and you don't use Lombok, it's kind of painful, but I don't love Lombok. which I guess is a very common sentiment. not so much because of sort of it removes some of the abstraction of the encapsulation and things like that but I use it because JPA is just painful without it and records can't be entities and all of that sort of thing. but yeah, it's it's a tough question and what the way I see it is not something like Lombok. Lombok changes how Java C works and that's kind of a problematic area. What we're doing is we're generating mostly external things and wiring them in during the build process. So it's mostly generated code.

**[16:08]** In some cases where we do things like transaction annotations and things like that, we do actually change the method for cases because that's kind of how the spring API expects things to be and we want it to be as performant as possible. We don't want to add additional layers. We me measure the actual generated assembly for all of these things to get to a level of performance that you would kind of expect from something like that. I think I have the similar experience with Lombok. I've tried it a couple of times and didn't like it. But I have another experience of a library that does a codegen and does it well and it sort of replaced GPA and M for us. It was Juke if you heard about it.

**[16:57]** Juke. Yes. Yes. Yes.

**[16:59]** Yeah. I remember that experience as pleasant and living without oil was something special. [snorts]

**[17:09]** Mhm. Isn't Juke not one of those opensource projects which became really a product and the developer can really live from what he has built. I think it's a great tool. Simon Martinelli is also a big fan and writing about it also on Foojay. so Juke is Lombok but a better version.

**[17:32]** Well Lombok is more about general code writing removing [clears throat]

**[17:40]** Repeating lines of code and JUK is strictly about the database data

**[17:45]** Area. Yeah.

**[17:45]** Yeah.

**[17:46]** Yeah. Okay. yeah you mentioned sh the records. that's one of those Java evolutions that tries to solve recurring problems not everything like the entities you mentioned. startup time we have project Leiden which is coming which will bring quite some improvements in the startup. there is an idea I don't know how far it is to integrate a JSON API into Java. Is Java getting better in solving things that were only possible with libraries?

**[18:24]** Well, with Java 25, it's much much better in terms of both startup time and everything. we had benchmarks where we beat Java 8, which was enough of an effort to do in terms of performance, but Java 25 is so much faster. especially with the AOT improvements although the AOT that we have right now isn't really the full story as you mentioned of the data and it's not actual binaries so we still beat Java 25 with AOT in terms of startup in terms of all of that in terms of overhead in general for most benchmarks not everything obviously because you can't beat a JIT I think one of the difficulties with Java is kind of with Java 9, we kind of made I think a wrong turn in terms of the way that module system was integrated into Java

**[19:26]** Because up until then we kind of built a monolithic Java which has its advantages because we already have a system of segregating Java which is packages and that's why what we try to do with code name one we try to look at packages of you use this thing, you only pay for that thing. You don't need to pay for anything else. So, we don't need a module system. We don't need any of that. But Java 9 kind of broke that. They threw out Jax me. They threw out all sorts of things. And now they're going and kind of trying to pull back things in and even for things like string templates for things that are relatively simple that should have been there ages ago. you know, formatting a string that 101 in terms of programming and we we're still stuck in the dot format stuff. so I think there's a cultural problem there which I get. I love that Java is conservative but it's I'm not holding my breath.

**[20:34]** Yeah. Dimitri, how the company you work for, which version of Java are you on?

**[20:41]** 21. Yeah,

**[20:43]** 21. Okay. if you look at it and how Java is evolving, why are you still on 21? Why not on 25? Is 21 good enough for what you're doing?

**[20:56]** Well, it's a curse of an endless backlog. You know, [laughter] there is something more important. Yeah. Well,

**[21:05]** You're ahead of the curve if they're on 21. So, that's indeed. That's a good answer.

**[21:12]** Yeah, I was just going to say you're lucky that you're not at a company that's still on Java 8 or seven or six like I talked with a few people [laughter] at the conference lately. yeah, you get the benefits of

**[21:24]** Java 5 compatibility, you know, so it's it's out there. Mhm.

**[21:31]** This whole reflection thing does this improve across Java versions is this something which will be different with project lighten for instance if we comp do a test run up front to start our application faster and everything has already been seen and loaded and handled. Any idea if this will evolve? No, because first of all, it's already improved. The Jet did an amazing job in optimizing away some of the overhead. But there are two options. Either you don't need reflection at all, which means, you know, you're just using it sort of as a an abstraction instead of using an interface or something like that. And then you're giving work for the JIT that you're not going to get back. You know, the JIT needs to do that. And every time it does an undo and undoes an optimization which happens more often than people who haven't done assembly realize because lots of optimization sort of go bad. So every time you pay for that and it might not be paid in the immediate CPU that you feel but it will be paid because the JIT is actually really good at splitting things among various cores. So you might

**[22:50]** Not see it in the bottom line of the benchmarks that you run, but it will be paid in energy and in other costs because it takes away from something else. So there's a price and it will always be there. You might not see it as much as you see it normally, which today it's really really efficient, but it's efficient as it runs for a while. the initial startup there's not much they can do except for as you said in Leen if they cash a benchmark result I think the sort of the initial result approach is problematic because you're optimizing for a benchmark and you get something that's good but not real for instance we support DJ which is the langu approach for doing a benchm mark run, but it's off by default and we never use it in our benchmarks because I think of it as kind of like cheating because you're running sort of a warm-up that isn't really how the app will necessarily represent in practice.

**[23:58]** So, we have it there because there's some special cases that might actually benefit from it, but I kind of think of it as iffy and I don't like the leaden approach of doing that. but it's a personal opinion

**[24:15]** To rephrase you think that using reflection is a sign of weak design right

**[24:26]** So back to the architects [laughter] if we look into how this system works on your side sh so you have a maven plugin who handles code and are you not making the build time longer then or is it a win anyhow?

**[24:46]** Well, there's a build time cost obviously. So, it's still relatively quick when you add everything up. So, for instance, comparing we do the same thing in our backend project and we benchmarked in our Foojay article. I'm not sure if it was published yet or not, but we benchmarked also the build time compared to Spring of the Java version, not the full backend version. and we build I think 30 or 40% faster than Spring which isn't much you know because Spring is pretty slow to build but we still so there is a cost during compile but I' I'd pay any cost during compile not any most cost during compile so my customers won't and I think that's that's fair

**[25:45]** We are recording this on October the 13th. I see that there is a post scheduled from you a smaller faster Java server with the Springstyle API for October 16th. That's the one. Yeah. So, by the time this podcast get published, people can also read that on Foojay. anything you see, Dimmitri, on the time you need to build your app versus running it versus loading versus warm up, do you see a shift using the tools that you've used in the article?

**[26:14]** Yeah. Well, I come from the world of banking of high performance apps, scaling architectures and for me build time is not that important as performance and res and resource usage. So [snorts] unless it's unfeasible, it's okay.

**[26:35]** You can spend some minutes more on building and testing.

**[26:38]** Yeah, drink coffee. [laughter]

**[26:40]** Yeah, drink coffee. have a talk with your colleagues. But I think what project Leiden will introduce is that you have this warm-up run in your CI CI/CD flow. Yeah, we'll again make this build flow a bit longer, but you will win. Is that a shift in Java finally towards cloud native environments? the thing you are actually trying to solve with code name one also for instance

**[27:11]** I'm not sure the thing I most liked about Leiden is the small object headers which made a huge difference in terms of memory I don't think the jump from eight to four will make that much of a difference we already made that and it didn't really move the needle as much but the jump to eight bytes object headers huge advantage the problem is that the startup time is so stark even compared to grow VM which I think will always be more efficient than Leiden because it's literally native code. I think Leiden might be able to come close to matching it when it actually ships with the full binary compilation, but it's still the 4JVM. And the 4JVM just can't possibly know what's going to happen at that stage.

**[28:07]** It needs the actual application to run. And you can't really cheat that. And we kind of cheated. That's our solution. We cheated by saying we're not really Java. Not in that way. We're coding one. We'll give you the same experience. You can't really tell the difference. Coke or Pepsi, you know, blind test, sort of thing. But, we'll give you that thing that you're paying for. We'll remove it and we'll let you see if you can tell the difference and how important that is.

**[28:40]** So, in the articles, Dimmitri, you were working on the back end. In that article, Shai, you were working on the UI. Is there still a match between those systems? Architectural we need existing codes, generated codes we don't mind, but not reflection integration. Is that a bit the same thing you both consider? for me it's more about the tradeoff between using some magic reflection using library and spending a bit more time and effort to build some solutions that would suffice my needs. I think historically there was a big difference and indeed the original JIT had the server mode and client mode and the way we saw it was okay client can show J GC stalls but it can pay more in terms of interpreter speed because you might not notice these differences as long as the performance is sort of smooth and the value of the long running JIT is much smaller in a client. So, you want the short-term wins, you don't necessarily want the long-term wins that you would want in a server. The thing is that all of that kind of changed in the last 20 years. servers are now more ephemereral than they used to be. We

**[30:10]** Don't expect them to run for years. We kind of change them on the fly. And a lot of these ideas that were once true for client side in terms of small size smaller RSSGC's all of those kind of transitioned and are almost universally true. So a server isn't a mobile app. There's still fundamental differences energy usage memory sort of restraints and the things like that. But when we're running in a container, the fact that I can run a server in a 300 200 megabyte container makes a difference now and it's not something that we could have done historically, but with optimizations that were geared towards mobile and even with the rise of ARM, if we're getting into that already, it's it's significant and we're seeing more and more consolidation in that sense. also the full stack. It's I think a huge selling point and I missed that. I missed having Java all the way through and I hope we'll be able to bring that back.

**[31:20]** Yeah, you probably know I'm a big Java VIX fan, but that's indeed the thing I'm missing is the full stack towards mobile and web. Yeah, I found Vadin a very good replacement for the web part, but then still I'm missing the mobile part. And we have by the way if we're talking we our JavaScript front end is open source so you can also build with that and get front end on the web as well. And also spoiler for upcoming stuff we're introducing compatibility for swing and for some a subset of JavaFX in coding one. So you could actually take your existing JavaFX apps, maybe modify a few things and run them. No reflection though.

**[32:07]** Shine, you're not only a good developer and a writer, but also a good salesperson for your project. [laughter] we're now talking a lot about the runtime and the JITS. there's also the point of view from the developer. So Shai, you went from open API schema files to Java objects. Dimmitri, you did the same with the Aaro files. Is having your data model as real Java objects, Java codes a big advantage if you're doing development?

**[32:41]** For me, it's more about having the source of truth in a single place. You know, it makes things much much easier to comprehend.

**[32:51]** And what is your source of truth? Is it Java to Avo? Is it Avo Java? Well, it's the schema basically and we generate the code around the schema and the schema is at central part of it all.

**[33:07]** Mhm. Mhm. same for you Shai.

**[33:11]** As you might know, I wrote a book about debugging. So for me that's a huge part of it and the fact that I can actually when we generate something I can actually stop and align in the debugger and see the object and it's the real object actually go into the source even if it's generated and understand what's going on there. That's that's a sale point for me that ability and I'm very obsessed with observability and everything. So losing that with the AOT compilation is kind of a painful point. So we added sort of seamless hotel integration there also through code generation. So you just declare hotel and sort of get that as well and you can debug that too which is exactly what I'm doing. You know

**[34:02]** Debugging good point. Is that related to serialization? Use it don't use it. it will make debugging easier.

**[34:12]** I think it's not much related because serialization decization can be basically unit tested pretty well. So,

**[34:22]** But we have unit test and then we have what happens in production. [laughter]

**[34:25]** Yeah. All the performance issues out of memories we get at night. Yeah. [gasps and snorts]

**[34:33]** Sh you have a book about this so you have a clear opinion here. you know how do you debug the unit test when it failed? So you need and how do you develop the unit test? You need to kind of you put a break point you see oh I got this here and you just write the code to match what you have to sort of prevent a potential regression or test against that. And debuggers are often, you know, people look at the debugger as sort of only a tool that they use when they have a problem rather than a tool that we should use all the time. And this is kind of a pet peeve of mine that you know you should always place breakpoints and verify assertions before you even write the unit test. You should be familiar with the state of the code because you don't necessarily expect a variable to have a particular value and it has it when you kind of look at its actual state.

**[35:36]** Dimmitri, you said you work in banking and very heavy applications. at what point should I worry about using serialization or not? Is this an important topic in a small project? Is it only in big projects with a lot of traffic and a lot of data being handled? Is there a threshold? Why should I consider my options?

**[36:03]** Well, I think it's not so much about the performance because Jackson works exceptionally well especially with that black bird thing that optimizes code on the fly. I think it's more about how you write your code and how clean your abstractions are because the cleaner the abstractions are the easier it is to debug and write tests.

**[36:32]** Same question for you. It's code name one for big projects, small projects, everything.

**[36:39]** Well, I'd say everything depending on how you define everything. So for instance, there's I work a lot in enterprise environments and banks and everything and lot of banks use us for the client side. I'm not sure if we would make as much sense in their back end because integrating with some legacy systems that banks use, we might not get there in the next few years because it's a complex environment. But because of the approach of microservices and things like that, there's room even in these environments. That's one of the reasons I wanted open telemetry to go in because the ability to have a trace that also includes us is crucial if you want to go into any enterprise environment today and that integrates with Dino trace with data dog with anything that you have out the I'd like though to talk a bit about the serialization aspect and about security in that sense because if we're looking at almost any vulnerability in Java over the past few decades it's serialization class loaders and reflection that they start there they might not be the root cause where that started it but they are somewhere along

**[38:04]** The chain of exploitation and when people that that's a huge part of the price that people pay for these sort of I'll call them features but their capability ies that most people don't really use or need and they have this sort of hidden potential flaw. I remember when Log4j came out and I noticed that grow VM wasn't vulnerable and the reason it wasn't vulnerable is because it didn't have the ability to sort of serialize something external and be able to sort of fall to that exploit not because of something intentional from their developers. it sort of didn't have that. And that's a really important part. If you're doing reflection, if someone in your name is doing reflection, that's a problem.

**[38:58]** So if I start any new fresh project now, I go to Java Wadori 26 27. I [snorts] use the latest and greatest. Should I still use reflection serialization in my project or try to avoid it completely? well I have mentioned before that we had to basically use Jackson because cotlin didn't have the feature of handling unknown fields but the thing is there is an open mesh request in the repo with exactly that. Yeah. And I'm still checking from time to time is it immersed or not. But yeah, but I'd say that Jackson works just fine from performance standpoint and if you would like to be an adopter of new technology, well, you're welcome.

**[40:02]** Yeah. Okay. Shai, what's your opinion? I think you can't really avoid reflection just like you can't avoid breathing if you're working with Java because it's there and people used it and if you're using any dependency downstream you're going to get reflection that's kind of unavoidable in most of these tools and that's I think that grow VM is a fantastic JVM that is hobbled not by the work of the amazing people that built it, but by the downstream complexity that the full Java ecosystem has. And when you start building it, something like Spring, which again great framework, I love working with it. But the downstream complexity where it brings everything in and then it can't I'll give you an example. Our back end is built with string and with ravm and we needed the dependent dependencies and we start just importing stuff in and our exe is 250 megabytes and that's really bad for a native deployment. It's it's not great and there's just no way to avoid it in regular Java as of now. And if you'd use code name one obviously not to do too much of a sales pitch obviously for an important project.

**[41:36]** [laughter]

**[41:38]** But yeah people can read all about that on Foojay. Okay. anything you want to add about this.

**[41:45]** Yeah. Yeah. I'd like to add that when we are in the world of fastpaced businessdriven decision springs starts to make more sense because you can prototype things much faster and yeah it comes with a price of course but you can be really fast if you want. M yeah indeed. Sh.

**[42:09]** Oh, completely. That's why we're copying their API. I 100% love Spring and I think a lot of the capabilities in Spring are really well designed and thought out. So the things like the Spring tests and stuff like that, we tried to get it to as compatible as possible. So you can just take the tests also the annotations obviously different package names and stuff like that. But the interface for the developers, it's fantastic. The thing is you need the runtime overhead of that. And I think a lot of the functionality like dependency injection, you need the runtime overhead if you're going to do a JE style deployment where you replace all sorts of things in runtime. But do you actually need that for actual most real production? If you're building a banking application, maybe if you're going into the what was historically Java E, J2E, all of that stuff, maybe.

**[43:10]** But most of us building in Java today probably don't really need all of that and don't really use these features at all and we're paying for them. So it's up to research what's your possibilities and pick the right one. It all depends on what you actually need, what you should use and then yeah we have the great articles by you to help people decide on what they should use and how they should build things. Okay. thanks a lot for your time. thanks a lot also to you for listening to this podcast. That's it for this episode. Dimitri and Shai, thank you both for showing how to be from very different corners of the GVM ecosystem, how you arrived at the same conclusion about reflection. and for being honest about where compile time code generation still can cost you something. Thank you listeners for tuning in. If you enjoyed this conversation, subscribe to the Foojay podcast on YouTube or in your favorite podcast app so you don't miss the next episode. See you on Foojay.io for more podcasts and more articles by Dimmitri Shai and all the other amazing authors of Foojay. Thank you.

**[44:24]** Give me a give me a J. Give me the OpenJDK.
