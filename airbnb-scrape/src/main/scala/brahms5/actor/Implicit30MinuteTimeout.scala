package brahms5.actor

import java.util.concurrent.TimeUnit

import akka.util.Timeout

trait Implicit30MinuteTimeout {
  final implicit val timeout = Timeout(30, TimeUnit.MINUTES)
}
