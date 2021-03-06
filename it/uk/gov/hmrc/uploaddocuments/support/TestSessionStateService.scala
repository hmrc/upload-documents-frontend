package uk.gov.hmrc.uploaddocuments.support

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import uk.gov.hmrc.uploaddocuments.journeys.State
import uk.gov.hmrc.http.HeaderCarrier

/** Intended to be mixed-in when binding a real service for integration testing, exposes protected service's methods to
  * the test.
  */
trait TestSessionStateService {

  def set(state: State, breadcrumbs: List[State])(implicit
    hc: HeaderCarrier,
    timeout: Duration,
    ec: ExecutionContext
  ): Unit = Await.result(save((state, breadcrumbs)), timeout)

  def setState(
    state: State
  )(implicit hc: HeaderCarrier, timeout: Duration, ec: ExecutionContext): Unit =
    Await.result(save((state, Nil)), timeout)

  def get(implicit
    hc: HeaderCarrier,
    timeout: Duration,
    ec: ExecutionContext
  ): Option[(State, List[State])] = Await.result(fetch, timeout)

  def getState(implicit hc: HeaderCarrier, timeout: Duration, ec: ExecutionContext): State =
    get.get._1

  def getBreadcrumbs(implicit
    hc: HeaderCarrier,
    timeout: Duration,
    ec: ExecutionContext
  ): List[State] = get.get._2

  def fetch(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[(State, List[State])]]

  def save(
    s: (State, List[State])
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[(State, List[State])]
}
