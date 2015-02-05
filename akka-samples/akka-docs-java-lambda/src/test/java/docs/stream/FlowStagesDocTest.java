/**
 * Copyright (C) 2015 Typesafe Inc. <http://www.typesafe.com>
 */
package docs.stream;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

import akka.actor.ActorSystem;
import akka.stream.ActorFlowMaterializer;
import akka.stream.FlowMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.KeyedSink;
import akka.stream.javadsl.RunnableFlow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.stage.Context;
import akka.stream.stage.Directive;
import akka.stream.stage.PushPullStage;
import akka.stream.stage.PushStage;
import akka.stream.stage.StageState;
import akka.stream.stage.StatefulStage;
import akka.stream.stage.TerminationDirective;
import akka.testkit.JavaTestKit;

public class FlowStagesDocTest {
  
  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("FlowStagesDocTest");
  }

  @AfterClass
  public static void tearDown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  final FlowMaterializer mat = ActorFlowMaterializer.create(system);
  
  static //#one-to-one
  public class Map<A, B> extends PushPullStage<A, B> {
    final private Function<A, B> f;
    public Map(Function<A, B> f) {
      this.f = f;
    }
  
    @Override public Directive onPush(A elem, Context<B> ctx) {
      return ctx.push(f.apply(elem));
    }

    @Override public Directive onPull(Context<B> ctx) {
      return ctx.pull();
    }
  }
  //#one-to-one

  static //#many-to-one
  public class Filter<A> extends PushPullStage<A, A> {
    final private Predicate<A> p;
    public Filter(Predicate<A> p) {
      this.p = p;
    }
  
    @Override public Directive onPush(A elem, Context<A> ctx) {
      if (p.test(elem)) return ctx.push(elem);
      else return ctx.pull();
    }

    @Override public Directive onPull(Context<A> ctx) {
      return ctx.pull();
    }
  }
  //#many-to-one

  //#one-to-many
  class Duplicator<A> extends PushPullStage<A, A> {
    private A lastElem = null;
    private boolean oneLeft = false;

    @Override public Directive onPush(A elem, Context<A> ctx) {
      lastElem = elem;
      oneLeft = true;
      return ctx.push(elem);
    }

    @Override public Directive onPull(Context<A> ctx) {
      if (!ctx.isFinishing()) {
        // the main pulling logic is below as it is demonstrated on the illustration
        if (oneLeft) {
          oneLeft = false;
          return ctx.push(lastElem);
        } else
          return ctx.pull();
      } else {
        // If we need to emit a final element after the upstream
        // finished
        if (oneLeft) return ctx.pushAndFinish(lastElem);
        else return ctx.finish();
      }
    }

    @Override public TerminationDirective onUpstreamFinish(Context<A> ctx) {
      return ctx.absorbTermination();
    }

  }
  //#one-to-many
  
  static//#pushstage
  public class Map2<A, B> extends PushStage<A, B> {
    final private Function<A, B> f;
    public Map2(Function<A, B> f) {
      this.f = f;
    }
  
    @Override public Directive onPush(A elem, Context<B> ctx) {
      return ctx.push(f.apply(elem));
    }
  }

  public class Filter2<A> extends PushStage<A, A> {
    final private Predicate<A> p;
    public Filter2(Predicate<A> p) {
      this.p = p;
    }
  
    @Override public Directive onPush(A elem, Context<A> ctx) {
      if (p.test(elem)) return ctx.push(elem);
      else return ctx.pull();
    }
  }
  //#pushstage
  
  static //#doubler-stateful
  public class Duplicator2<A> extends StatefulStage<A, A> {
    @Override public StageState<A, A> initial() {
      return new StageState<A, A>() {
        @Override public Directive onPush(A elem, Context<A> ctx) {
          return emit(Arrays.asList(elem, elem).iterator(), ctx);
        }
      };
    }
  }
  //#doubler-stateful

  @Test
  public void demonstrateVariousPushPullStages() throws Exception {
    final KeyedSink<List<Integer>, Future<List<Integer>>> keyedSink = 
        Sink.<List<Integer>>head();
    final Sink<Integer> sink = Flow.of(Integer.class).grouped(10).to(keyedSink);

    //#stage-chain
    final RunnableFlow runnable = Source.from(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
      .transform(() -> new Filter<Integer>(elem -> elem % 2 == 0))
      .transform(() -> new Duplicator<Integer>())
      .transform(() -> new Map<Integer, Integer>(elem -> elem / 2))
      .to(sink);
    //#stage-chain

    assertEquals(Arrays.asList(1, 1, 2, 2, 3, 3, 4, 4, 5, 5), 
        Await.result(runnable.run(mat).get(keyedSink), FiniteDuration.create(3, TimeUnit.SECONDS)));
  }
  
}
