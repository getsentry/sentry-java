package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.SpanStatus
import org.junit.Test

class NavLeaseCoordinatorTest {

  @Test
  fun `data ownership changes once per claim and can only be released by its owner`() {
    val coordinator = NavLeaseCoordinator()
    val firstOwner = Any()
    val secondOwner = Any()

    assertThat(coordinator.hasDataLease(firstOwner)).isFalse()

    assertThat(coordinator.claimDataLease(firstOwner)).isTrue()
    assertThat(coordinator.claimDataLease(firstOwner)).isFalse()
    assertThat(coordinator.claimDataLease(secondOwner)).isTrue()

    assertThat(coordinator.releaseDataLease(firstOwner)).isFalse()
    assertThat(coordinator.hasDataLease(secondOwner)).isTrue()

    assertThat(coordinator.releaseDataLease(secondOwner)).isTrue()
    assertThat(coordinator.hasDataLease(secondOwner)).isFalse()
  }

  @Test
  fun `owners are distinguished by identity even when structurally equal`() {
    val coordinator = NavLeaseCoordinator()
    val firstOwner = listOf("same")
    val secondOwner = listOf("same")

    coordinator.claimDataLease(firstOwner)
    coordinator.claimTransactionLease(firstOwner)

    assertThat(coordinator.hasDataLease(secondOwner)).isFalse()
    assertThat(coordinator.hasTransactionLease(secondOwner)).isFalse()
  }

  @Test
  fun `transaction handoff finishes the previous transaction with its existing status`() {
    val fixture = NavTestFixture()
    val firstOwner = Any()
    val secondOwner = Any()
    val tx = fixture.transaction()
    tx.status = SpanStatus.CANCELLED
    fixture.scope.transaction = tx
    fixture.coordinator.claimTransactionLease(firstOwner)
    fixture.coordinator.bindTransaction(firstOwner, tx)
    fixture.coordinator.claimTransactionLease(secondOwner)
    assertThat(tx.isFinished).isFalse()

    fixture.coordinator.prepareTransactionUpdate(secondOwner, fixture.scope)

    assertThat(tx.isFinished).isTrue()
    assertThat(tx.status).isEqualTo(SpanStatus.CANCELLED)
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `transaction handoff defaults an unset status to OK`() {
    val fixture = NavTestFixture()
    val firstOwner = Any()
    val secondOwner = Any()
    val tx = fixture.transaction()

    fixture.coordinator.claimTransactionLease(firstOwner)
    fixture.coordinator.bindTransaction(firstOwner, tx)
    fixture.coordinator.claimTransactionLease(secondOwner)
    fixture.coordinator.prepareTransactionUpdate(secondOwner, fixture.scope)

    assertThat(tx.isFinished).isTrue()
    assertThat(tx.status).isEqualTo(SpanStatus.OK)
  }

  @Test
  fun `handoff preserves a host transaction that replaced the navigation transaction`() {
    val fixture = NavTestFixture()
    val firstOwner = Any()
    val secondOwner = Any()
    val navTx = fixture.transaction("navigation")
    val hostTx = fixture.transaction("host")

    fixture.coordinator.claimTransactionLease(firstOwner)
    fixture.coordinator.bindTransaction(firstOwner, navTx)
    fixture.scope.transaction = hostTx
    fixture.coordinator.claimTransactionLease(secondOwner)
    fixture.coordinator.prepareTransactionUpdate(secondOwner, fixture.scope)

    assertThat(navTx.isFinished).isTrue()
    assertThat(hostTx.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTx)
  }

  @Test
  fun `non-owner cannot bind or stop another owners transaction`() {
    val fixture = NavTestFixture()
    val firstOwner = Any()
    val secondOwner = Any()
    val tx = fixture.transaction()

    fixture.coordinator.claimTransactionLease(firstOwner)
    fixture.coordinator.bindTransaction(firstOwner, tx)
    fixture.coordinator.bindTransaction(secondOwner, fixture.transaction("unowned"))
    fixture.coordinator.prepareTransactionUpdate(secondOwner, fixture.scope)
    fixture.coordinator.stopOwnedTransaction(secondOwner, fixture.scope)
    assertThat(tx.isFinished).isFalse()

    fixture.coordinator.stopOwnedTransaction(firstOwner, fixture.scope)

    assertThat(tx.isFinished).isTrue()
  }

  @Test
  fun `releasing an incoming owner restores ownership to the still active transaction owner`() {
    val fixture = NavTestFixture()
    val firstOwner = Any()
    val secondOwner = Any()

    fixture.coordinator.claimTransactionLease(firstOwner)
    fixture.coordinator.bindTransaction(firstOwner, fixture.transaction())
    fixture.coordinator.claimTransactionLease(secondOwner)
    fixture.coordinator.releaseTransactionLease(secondOwner)

    assertThat(fixture.coordinator.hasTransactionLease(firstOwner)).isTrue()
    assertThat(fixture.coordinator.hasTransactionLease(secondOwner)).isFalse()
  }

  @Test
  fun `finished transaction owners are not restored after releasing an incoming owner`() {
    val fixture = NavTestFixture()
    val firstOwner = Any()
    val secondOwner = Any()
    val tx = fixture.transaction()

    fixture.coordinator.claimTransactionLease(firstOwner)
    fixture.coordinator.bindTransaction(firstOwner, tx)

    tx.finish()

    fixture.coordinator.clearFinishedTransaction()
    fixture.coordinator.claimTransactionLease(secondOwner)
    fixture.coordinator.releaseTransactionLease(secondOwner)

    assertThat(fixture.coordinator.hasTransactionLease(firstOwner)).isFalse()
    assertThat(fixture.coordinator.hasTransactionLease(secondOwner)).isFalse()
  }

  @Test
  fun `releasing a former owner leaves the current transaction lease intact`() {
    val coordinator = NavLeaseCoordinator()
    val firstOwner = Any()
    val secondOwner = Any()
    assertThat(coordinator.claimTransactionLease(firstOwner)).isTrue()
    assertThat(coordinator.claimTransactionLease(firstOwner)).isFalse()

    coordinator.claimTransactionLease(secondOwner)
    coordinator.releaseTransactionLease(firstOwner)

    assertThat(coordinator.hasTransactionLease(secondOwner)).isTrue()
  }
}
