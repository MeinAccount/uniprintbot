package heiko

import java.util.*

val positiveStickers = arrayOf("CAADAgADnAIAAj-VzAovQnDNQQe33QI", "CAADAgADnwEAAgeGFQfRYJU4HtbsvAI",
        "CAADAgADuAUAAvoLtghew_BTab-Q-QI", "CAADAgADtQUAAmMr4gm433nFpTkpEAI", "CAADAgADBwIAAtzyqwdVSve97Ve_kQI",
        "CAADAgADewUAAhhC7ggL5p5h3oDTAwI", "CAADAgADjAIAAj-VzArhgivUMNJrhgI", "CAADAgAD6QAEOKAKSndrbn6hA3EC",
        "CAADAgADfQMAAsSraAvoUE-v_dODPwI", "CAADAgADyAUAAvoLtgi1ezx5lIjuZwI")
val unknownStickers = arrayOf("CAADAgAD3wEAAjZ2IA4lakrBbQW2kwI", "CAADAgADIAYAAhhC7ggr3IgQ7CWJQgI",
        "CAADAgADbAUAAhhC7gjcgXuEy0jstgI", "CAADAgADawUAAhhC7gi-w6EkmXJ3sgI", "CAADAgADQwMAAsSraAvAEH7acBV_uAI",
        "CAADAgAD0AUAAvoLtgheMMFYmtIxMQI", "CAADAgADzwIAAj-VzAqZJmrw1nWAUAI", "CAADAgADfAUAAhhC7gjEYV0FBA_xjgI")
val negativeStickers = arrayOf("CAADAgADIAADyIsGAAGwI-I5pMSEdQI", "CAADAgADLwIAArrAlQXCB-MwsRsKUAI",
        "CAADAgAD6AMAAvJ-ggyV1koZSeQd7QI", "CAADAgADdQIAAsSraAthqwkz4CCMGwI", "CAADAgADKwIAAj-VzAq8_jVvbB-ZgQI",
        "CAADAgADTAUAAmMr4glGKjnwtWFTIAI", "CAADAgAD6wEAAiCBFQABCQn4d2vDOrcC", "CAADAgAD5QADNnYgDr7EklL1F-d-Ag",
        "CAADAgADuQEAAgeGFQcm74jOQU-L8wI")


private val random = Random()
fun <T> Array<T>.random() = get(random.nextInt(size))
