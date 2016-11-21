heroku web autoscaler
=====================

## TL;DR

Hackday's piece of software that acts as a logdrain and scales the dynos to handle current load.

## Solution

Solution consists of three parts:
  * web dyno that serves as log drain for autoscaled application - its sole responsibility is to filter out log entries which are not router entries (unfortunately most) and put them on SQS queue
  * SQS queue consumer that takes log entries and stores application service stats to redis
  * scaling observer - batch worker that observes given time window stats and reacts upon them

## Web dyno

It appeared to be more complicated than I initially thought - even though the data format is simple, I had to struggle a bit with regexes ;). The thing is that web dyno filters out all the entries that are not router entries, and the rest is just sent straight to SQS queue.

## Queue consumer

So the idea is that consumer is dividing the time into 10s slots and dumps two statistics into redis:
  * `hitcount` - which is darn simple, because all you have to is to `INCR` keys in redis
  * `avgServiceTime` - which is a moving an average counted with a given formula, for n-th hit we just make `avgServiceTime = ((n - 1)*originalAvgServiceTime + newServiceTime)/n`

## Scaling observer

The scaling logic

### The math behind

The basis for the scaling observer is an assumption that work needed to handle a typical request is linear. The initial idea was that there is an equation:

```
c = NT / (ceil(H/N) * N) = T / ceil(H/N)
```

where: 
  * N - number of dynos
  * T - average service time
  * H - hit count in a given period of time
  * c - some magical constant

so the idea is that deriving the magical `c` constant from a series of measurements, we should be able to predict the needed number of dynos for a hit rate observed at a given point of time :sparkles:. Having a derived value `C` (out of historical knowledge of some time period), and current hit rate `H`, we can use above formula :point_up: to count number of needed dynos in current state.

### Critique

Those were mostly the reasons why it has taken me so long to write observations down:

#### Fluctuations of c (solved)
The initial problem I observed was that the c (I call it ratio) was fluctuating over time quite a lot. The idea is to keep a buffer c of values over a period of time, and use this knowledge to derive C as a median of this series.

#### Fluctuations of derived dyno count (solved) 
Because the formula described here is pretty simple, its prone to hit rate peaks. Thats why I've taken 2 measures to not let algorithm to overreact: 
  * the basis for the decision is not last observed time period, but a 'sum' of observations for last minute. That has also made me to exchange `H` factor with `R` factor (`R` is hit rate, `R = H/(observation_time)`) to be able to normalize values over time (BTW - this conclusion has taken me a hell lot of time, ergo: my math skills are dead :( ).
  * the scaling decisions are throttled a bit 
    - when it comes to scaling up - algorithm can only scale up if the number of dynos was the same for the last minute
    - when it comes to scaling down - algorithm can scale down only when the number of dynos was the same for 10 minutes.

#### Low hit count / non-uniform hits distribution pitfall (solved)
This one was even more awesome than the ones described before. Lets assume that we are starting the autoscale process, and the hitrate is pretty low (literally weekend). The initial number of dynos is 8. The for `c` derived from formula above:
`c = NT / (ceil(H/N) * N) = T / ceil(H/N)`
so we can see that c grows high, especially if hit rate is low, and number of dynos is high. Lets imagine that we've got one hit in a given period of time, with service time=200ms. Even though the hit was served by one of the dynos, the algorithm taught itself that it needs 8 dynos to be able to serve 1 hit with 200ms time. So to serve 2 hits with 200ms we need 16 dynos! Similar situation (maybe less harmful) can happen when we have 16 dynos, and app was hit 17 times...

The solution is to simulate that number of hits is divisble by number of dynos. 

#### Avg time measuring pitfal (not solved)
While observing the high traffic caused by reindex operation, we (together with @mateusz-buczek) observed that the dyno count started to decrease even though the hit rate was quite high. The reason is that the inferred dyno count is highly coupled to recent average service time. While performing reindex operation there happen to be a lot of low-service-time GET operations which happen to make the real, mixed GET/POST/PUT traffic not visible in the calculated `c` value. Situation can be observed here (hit rate is green):

![image](https://uploads.github.schibsted.io/github-enterprise-assets/0000/0119/0000/3072/6e1b37fe-eed3-11e5-9b77-e8f33f44cfb9.png)


That could be overcome by:
  * taking into consideration eg 80th percentile of service time instead of 50th - that complicates information storing a bit
  * one could extend the time from which the 'knowledge' used to derive `c` value - but its hard to find a good value for it
  * store statistics per endpoint (so that resulting dyno count is a composition of `c` values calculated intelligently separately per each method/endpoint pair) - but thats a topic for a next hackday I guess

## Safety
The scaling decisions are done under following constraints:
  *  to be able to make scaling decision, sufficient knowledge has to be gathered (currently at least 10 minutes)
  * number of dynos can't go below 1 and above 16

## Potential extensions
  * the log drain is a web dyno that gets POST's with syslog entries - the only thing it does is to filter out garbage, and put rest into SQS. I think it would make to switch to [AWS Lambda](http://docs.aws.amazon.com/apigateway/latest/developerguide/getting-started.html) here
  * the information put to Redis is not fully atomic (becuase the average calculation), which could lead to some skewed results - I think introducing [Lua script](http://redis.io/commands/EVAL) that would update both avgServiceTime and hitcount could solve it 
  * to be able to count percentiles or some more fancy pantsy statistical methods on service times I think at some point it would make sense to save each request time alone in the redis list (additionally we get hit count for free)
