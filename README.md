## Dummy Wallet Design

### **Background**

According to the African Development Bank Group, in the year 2000, intra African trade accounted for a mere 10% of 
international trade involving African countries. While this stood at about 16% in 2014, it is evident that more trade 
between african countries is key to developing sustainable economies. 
One factor that has obstructed seamless intra African trade is the lack of a Pan African currency exchange. 
Hence, exchanging one African currency for another requires that, the source currency be converted to a more dominant 
currency like USD, in turn the USD is converted to the destination African currency. On the surface, this seems counter 
intuitive, however, this is precisely what happens today. 

The result of this is:
* Greater cost of trade between African countries
* Over reliance on USD. Statistics show that about 70% of international trade quoted in USD does not involve the 
Unites States of America

What we propose in this document, is the development of a Pan African Corda business network, within which buyers and 
sellers of African currencies are matched, such that money need not move across borders, rather every currency stays 
within its shores.


### Key Concepts

#### Personas
1. Issuer: this may be the business network operator. The issuer is responsible for mirroring fiat onto the ledger.
Fiat is mirrored on a one to one basis, hence the issuer must ensure this fiat currency is securely backed in a 
financial institution. 
KYC and AML checks must also be performed by this persona before issuing money to any party on the ledger. In short, the
issuer is the entry point for money. 

2. Gateway: there may be as many gateways as possible, but at least one must be present in every country housing the
currency one wishes to transact in. Just as the name suggests, it is the entry point for customers and the means through
which their wallets are funded, or withdrawn from. Hence, these gateways must be well known trust worthy financial
institutions possessing some liability with the central bank of their host nation.
These gateways provide infrastructure for various channels through which customers can fund or withdraw from their 
wallets. 
In fact, gateways are responsible for creating **CustomerOwned** and **LiquidityProviderOwned** wallets (more on this).

3. Customer: these are the regular users who wish to perform basic transactions. Bill payments, intra and inter currency
transfers, etc. 

4. Liquidity Provider: for cross currency transfers, some form of exchange must be implemented. In this business
network, we propose a market driven by buy and sell orders. A liquidity provider is any trusted entity or individual,
owning wallets denominated in at least 2 currencies, and willing to exchange one these currencies for another.


#### Wallets
Inheriting directly from persona types, there is a wallet type for each:

1. Issuer owned: these are specially reserved, as transactions may not originate from these kinds of wallets. One use of
these wallets is holding fees due the Issuer.

2. Gateway owned: this wallet is created by the issuer as part of the Gateway setup process. Before Gateway setup, KYC
and AML checks must be duly performed, beyond which the Gateway node is setup, and this wallet type created.
If there is a money pledge from the gateway, it is reflected in this wallet. This wallet serves as a facilitator for
customer transactions. 

3. Customer owned: a customer wallet is created via a gateway. This wallet holds the funds of a customer, and the
customer may freely perform transactions from this wallet. Every wallet is bound to a specific currency, so a user
interested in owning multiple currencies must own multiple wallets. Customer wallets for a specific currency can only be
created by Gateways residing in the jurisdiction of that currency. Hence, a Kenyan Shillings wallet must be created by a
Kenyan Gateway and so on.

4. Liquidity provider owned: these wallets facilitate buying and selling of currencies. Liquidity providers create sell
entries into the order book linking these entries with 2 wallets denominated in the source and destination currencies.
An inter currency transfer is made possible by sending money to a LiquidityProviderOwned wallet, which in turn triggers
a transfer from the second LiquidityProviderOwned wallet to the destination wallet. One thing to note, these actions do
not follow one another, they all occur atomically.

#### Processes
* Issuance: the process through which fiat money is mirrored onto the ledger through a well known, trusted party. 
Money can be issued to a GatewayOwnedWallet only, every other wallet receives money via the GatewayOwnedWallet. 
Issuance may be performed as part of the setup of a Gateway or subsequently following rigorous checks and approvals.
Only an Issuer can initiate the process of Issuance. What ever money is issued onto the ledger must be securely backed
on a one to one basis in a bank

* Redemption: the process through which money as represented on the ledger is withdrawn. In simpler words, transferring
money from a wallet to the outside world, like a bank account

* Transfer: any movement of money from one wallet to another is a transfer

### High Level Architecture
Whilst the figure below displays the overarching goal of a massive compatibility zone, our focus for now is the business 
network.

***HIGH_LEVEL_ARCHITECTURE_IMAGE***

### Issuance
The on-boarding process for a Gateway would be done via the Business Network Membership Service. The entire flow of this 
process is not covered over here, what we concern ourselves with is how this on-boarding affects our Pan African Payment 
Ledger.

Proper KYC(Know Your Customer) and AML(Anti Money Laundering) checks must be performed on any financial institution
interested in operating gateway. These financial institutions are accountable to the central bank of their host country.
A float bank account is set up, and designated for PAPL transactions only. A messaging interface of any kind must also
be set up to enable Cordapps send instructions to the core banking infrastructure.
The financial institution may pledge a certain amount of money, which is mirrored onto the ledger by the Issuer party.
This money is held in a `GatewayOwned` wallet belonging to the financial institution in question. The Cordapp then
instructs the bank housing the float account to debit the float account of the pledged amount.

The interface to the float account is a necessary part of a gateway setup, as it ensures the one to one backing of fiat. 

Subsequently (after setup), issuance may occur for a Gateway. In this case, it must be manually triggered by authorised
personnel and pass through disparate levels of approvals.

Below are some states resulting from the setup of a gateway wallet and issuance.

##### *FloatAccountDetailsState*
```json
{
	"LinearId": "UUID",
	"Owner": "Party",
	"Country": "ISO Country Code",
	"AccountName": "String",
	"AccountNumber": "IBAN",
	"Currency": "ISO Currency Code",
	"Verified": "Boolean",
	"LastUpdated": "Instant"
}
```

##### *WalletState*
```json
{
  "LinearId": "UUID",
  "Owner": "Party",
  "CreatedBy": "Party",
  "Type": "GatewayOwned",
  "Currency": "ISO Currency Code",
  "Amount": "Amount",
  "Status": [],
  "Verified": "Boolean",
  "CreatedAt": "Instant",
  "LastUpdated": "Instant"
}
```

The issuance State keeps track of the total amount issued by the issuer. Every time an issuance occurs, this state is
updated.
##### *IssuanceState*
```json
{
	"LinearId": "UUID",
	"Issuer": "Party",
	"Recipient": "Party",
	"Currency": "ISO Currency Code",
	"Amount": "Amount",
	"Status": [],
	"LastUpdated": "Instant"
}
```

Standard Corda Cash states are issued to the Gateway, setting the owner as the subject Gateway. 
The full cash state model is not specified here, but here are some necessary fields:
##### *CashState*
```json
{
	"Issuer": "Party",
	"Currency": "ISO Currency Code",
	"Amount": "Amount",
	"Owner": "Party"
}
```
### Wallet Creation
`GatewayOwned` wallets are created by the Issuer, however every other wallet kind is created by a Gateway. Hence,
Gateways must be well known legal entities in their host countries. Wallets are tied to specific currencies and gateways
may only create wallets denominated in the official currency of their host country. A customer interested in owning
multiple currencies must open wallets via Gateways present in countries that use that currency.
In fact, a user need not trust any other network participant other than the Gateway, they also may not be aware of any
participant other than the gateway. Just as the name suggests, the Gateway is a passage into this business network.

Considering notion of identities within Corda are on a node level, it is impractical to set up a node for every customer.
Hence, `CustomerOwned` wallets are really owned by the Issuer, an extra mapping by `externalId` is used to differentiate
which wallets in the Issuer's vault belong to each individual customer. On creation of the wallet, by the Gateway, the
issuer's signature is necessary for creation to succeed. The `externalId` may be any unique identifier for an individual,
like an email address.

Creation of a `LiquidityProviderowned` wallet follows the same process as explained above, only with a higher barrier 
for entry, as these entities require deeper levels of KYC and AML checks.

##### *WalletState*
```json
{
  "LinearId": {
    "externalId": "ExternalID",
    "guuid": "UUID"
  },
  "Owner": "Party",
  "CreatedBy": "Party",
  "Type": ["IssuerOwned", "CustomerOwned", "LiquidityProviderOwned"],
  "Currency": "ISO Currency Code",
  "Amount": "Amount",
  "Status": [],
  "Verified": "Boolean",
  "CreatedAt": "Instant",
  "LastUpdated": "Instant"
}
```

### Funding Wallets
`GatewayOwned` wallets are funded via an issuance, however every other wallet kind is funded via a Gateway.
The Gateway provides various channels through which customers can fund their wallets, such as ATM, POS, Web, USSD etc.
A customer triggers a funding request via any of these channels, the customer is debited followed by a transfer of the
equivalent from the gateway's wallet to the customer's wallet.

As described in the previous subsection, from a Corda perspective, customer wallets are really owned by the issuer, but
the issuer uses externalIds to differentiate individual wallet owners. The same goes for funding a customer wallet, the
issued cash states bear the issuer as the owner.

##### *WalletFundingState*
```json
{
  "LinearId": "UUID",
  "InitiatedBy": "ExternalId",
  "Channel": ["ATM", "POS", "WEB", "MOBILE", "USSD", "COUNTER", "AGENT"],
  "Currency": "ISO Currency Code",
  "Amount": "Amount",
  "Status": [],
  "CreatedAt": "Instant"
}
```

##### *TransferState*
```json
{
  "LinearId": "UUID",
  "Source": "Party",
  "Destination": "Party",
  "SourceWallet": "WalletID",
  "DestinationWallet": "WalletID",
  "Currency": "ISO Currency Code",
  "Amount": "Amount",
  "Type": ["SingleCurrency", "CrossCurrency"],
  "Status": [],
  "CreatedAt": "Instant"
}
```

`WalletState` models are not displayed here for simplicity. What you must note is that a funding consumes the
gateway's wallet and customer's wallet, and produces new wallet wallet states for each representing the debit and credit
involved in this funding transaction.


### <a name="order-book"/> Order Book
As the start of this document, the importance of intra African trade was emphasised, the beauty of this business network
stems from cross currency transfers. The previous section speaking on funding wallets, scratched the surface of transfers,
but that information is enough to explain the concept of the order book.

The order book is driven by buy and sell entries. Users of one commodity interested in another commodity place a sell
offer. I am willing to sell X number of Item X' for Y number of item Y', while buyers simply do the reverse.
Liquidity providers are the sellers in our business network. They own some amount of a currency and are willing to 
exchange some amount of it for another currency at a specific rate.

When a customer wishes to transfer some amount of money to a wallet denominated in a different currency, the exchange is
performed by employing liquidity providers.

An Exchange rate feed is also needed to guide this market. We do not want liquidity providers over valuing or undervaluing
certain currencies for personal gain. Hence, this feed (with some allowed margin) is needed.
Statistics of seller offers could also be taken and only those that fall within the 25th to 75th percentile may be 
considered. This could serve to curb over valuation or undervaluation of certain currencies.

A simple example best explains the process of a cross currency transfer:

1. LiquidityProviderA creates a sell offer of GHS1,000 for NGN76,000
2. LiquidityProviderB creates a sell offer of GHS2,000 for NGN153,000
3. A individual in Nigeria wants to send NGN152,000 to a friend in Ghana
4. The Nigerian requests the best NGN to Cedis offer from the Order Book. It gets a quote of NGN76,000 for GHS1,000 and 
NGN76,000 for GHS993.46.
5. An atomic transfer of:
    1. NGN76,000 from the Nigerian's wallet to LiquidityProviderA's NGN wallet and GHS1,000 from LiquidityProviderA's GHS
     wallet to the Ghanaian's wallet
    2. NGN76,000 from the Nigerian's wallet to LiquidityProviderB's NGN wallet and GHS993.46 from LiquidityProviderB's GHS
     wallet to the Ghanaian's wallet

Note this example uses offers from 2 liquidity providers, this is on purpose as this would be a common scenario. Using
LiquidityProviderB for the full transfer would be less profitable than combining offers from LiquidityProviderA and 
LiquidityProviderB.

A liquidity provide may perform a transfer to another wallet not involving cross currency transactions. Such transfers
require an extra check to prevent the wallet balance from depreciating below the total number of orders tied to that wallet.

Remember, from the Corda point of view, customer owned wallets are really owned by the issuer, this is the same for a
LiquidityProviderOwned wallet. So customer to customer wallet transfers do not change the owner of the underlying cash
states, however the Wallet states are updated as this maps directly to individuals via their externalID.

Not all state models are represented in this section for simplicity, only states that have not been previously encountered
are listed.

For cross currency transfers, the customer sending the money is oblivious of the internal workings of this transfer, the
major info relevant to them is the amount sent, and to whom it was sent. `LiquidityProviderEnabledTransferState` and
`OrderMatchedState` are used to keep track of the liquidity provider hops. Every cross currency transaction involves a
minimum of 2 transfers, one from the sender's wallet to the liquidity provider wallet, second from the liquidity provider
wallet to the recipient's wallet. These 2 transfers are represented in different `LiquidityProviderEnabledTransferState`.
Both state are then referenced in an `OrderMatchedState`.

##### *LiquidityProviderEnabledTransferState*
```json
{
  "LinearId": "UUID",
  "Source": "Party",
  "Destination": "Party",
  "SourceWallet": "WalletID",
  "DestinationWallet": "WalletID",
  "Currency": "ISO Currency Code",
  "Amount": "Amount",
  "Status": [],
  "CreatedAt": "Instant"
}
```

##### *OrderMatchedState*
```json
{
  "LinearId": "UUID",
  "DebitLegStateRef": "StateRef",
  "CreditLegStateRef": "StateRef"
}
```


### Redemption
Gateways are the only exit point from this business network, this means that redemption/withdrawal can be performed via
a Gateway only. Gateways provide various channels through which customers can redeem funds. 
It involves a simple transfer from the customer's wallet to the gateway's wallet, followed by an instruction to the bank
to debit the float account, handing over the equivalent to the customer.

A customer owning a wallet may withdraw funds from any gateway, even when the Gateway's operates in a different
jurisdiction.
An example would best explain this;

* A Nigerian customer owning a wallet denominated in Nigerian Naira, visits kenya for a vacation. 
* This customer needs some cash to buy some goods from a market, but does not own a Kenyan bank account (Or Mpesa account)
* He visits a Gateway agent to assist with withdrawing money from his Nigerian Naira denominated wallet. 
* The money is transferred from the customer's Nigerian Naira wallet to the Gateway's Kenyan Shillings wallet 
(this is facilitated by liquidity providers as explained [here](#order-book)).
* This Gateway's float account is debited, then the agent hands the equivalent to the customer

##### *RedemptionState*
```json
{
	"LinearId": "UUID",
	"Gateway": "Party",
	"Source": "WalletID",
	"Channel": ["ATM", "POS", "WEB", "MOBILE", "USSD", "COUNTER", "AGENT"],
	"Currency": "ISO Currency Code",
	"Amount": "Amount",
	"Status": [],
	"CreatedAt": "Instant"
}
```
