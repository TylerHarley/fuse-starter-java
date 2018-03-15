package org.galatea.starter.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@NoArgsConstructor(access = AccessLevel.PRIVATE) // For spring and jackson
@Builder
@Getter
@Setter
@ToString
@EqualsAndHashCode
@Slf4j
@Entity
public class TradeAgreement {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  protected Long id;
  
  @NonNull
  protected String instrument;

  @NonNull
  protected String internalParty;

  @NonNull
  protected String externalParty;

  @NonNull
  protected String buySell;

  @NonNull
  protected Double qty;

}
