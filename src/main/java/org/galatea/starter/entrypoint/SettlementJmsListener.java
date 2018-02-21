
package org.galatea.starter.entrypoint;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import org.galatea.starter.domain.TradeAgreement;
import org.galatea.starter.entrypoint.messagecontracts.TradeAgreementTranslator;
import org.galatea.starter.service.SettlementService;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;


@RequiredArgsConstructor
@ToString
@EqualsAndHashCode
@Slf4j
@Component
public class SettlementJmsListener {

  @NonNull
  protected SettlementService settlementService;

  @NonNull
  protected TradeAgreementTranslator tradeAgreementParser;

  /**
   * Spawns Missions for any TradeAgreements pulled off the jms queue.
   */
  @JmsListener(destination = "${jms.agreement-queue}", concurrency = "${jms.listener-concurrency}")
  public void settleAgreement(final byte[] message) {
    log.info("Received message. Translating.");
    try {
      TradeAgreement agreement = tradeAgreementParser.translateToAgreement(message);

      log.info("Handling agreements {}", agreement);

      Set<Long> missionIds = settlementService.spawnMissions(Arrays.asList(agreement));
      log.info("Created missions {}", missionIds);
    } catch (InvalidProtocolBufferException e) {
      log.error("Invalid message received.", e);
    }
  }

}
