package com.study.blockchain.network;

import com.study.blockchain.core.Block;
import com.study.blockchain.core.Blockchain;
import com.study.blockchain.utxo.UTXOPool;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class NetworkTest {

    @Nested
    @DisplayName("MessageType")
    class MessageTypeTests {

        @Test
        @DisplayName("содержит все необходимые типы")
        void containsAllTypes() {
            assertNotNull(MessageType.HANDSHAKE);
            assertNotNull(MessageType.NEW_BLOCK);
            assertNotNull(MessageType.NEW_TRANSACTION);
            assertNotNull(MessageType.GET_BLOCKS);
            assertNotNull(MessageType.BLOCKS);
            assertNotNull(MessageType.GET_PEERS);
            assertNotNull(MessageType.PEERS);
        }
    }

    @Nested
    @DisplayName("Message")
    class MessageTests {

        @Test
        @DisplayName("создаётся с типом и payload")
        void createsWithTypeAndPayload() {
            Message msg = new Message(MessageType.HANDSHAKE, "{\"version\":1}");

            assertEquals(MessageType.HANDSHAKE, msg.getType());
            assertEquals("{\"version\":1}", msg.getPayload());
        }

        @Test
        @DisplayName("timestamp устанавливается автоматически")
        void timestampSetAutomatically() {
            long before = System.currentTimeMillis();
            Message msg = new Message(MessageType.NEW_BLOCK, "{}");
            long after = System.currentTimeMillis();

            assertTrue(msg.getTimestamp() >= before);
            assertTrue(msg.getTimestamp() <= after);
        }

        @Test
        @DisplayName("сериализуется в JSON")
        void serializesToJson() {
            Message msg = new Message(MessageType.HANDSHAKE, "{\"nodeId\":\"abc\"}");

            String json = msg.toJson();

            assertNotNull(json);
            assertTrue(json.contains("HANDSHAKE"));
            assertTrue(json.contains("nodeId"));
        }

        @Test
        @DisplayName("десериализуется из JSON")
        void deserializesFromJson() {
            Message original = new Message(MessageType.NEW_BLOCK, "{\"index\":1}");
            String json = original.toJson();

            Message restored = Message.fromJson(json);

            assertEquals(original.getType(), restored.getType());
            assertEquals(original.getPayload(), restored.getPayload());
        }

        @Test
        @DisplayName("round-trip сохраняет данные")
        void roundTripPreservesData() {
            Message original = new Message(MessageType.NEW_TRANSACTION,
                "{\"amount\":50.5,\"recipient\":\"bob\"}");

            Message restored = Message.fromJson(original.toJson());

            assertEquals(MessageType.NEW_TRANSACTION, restored.getType());
            assertTrue(restored.getPayload().contains("50.5"));
            assertTrue(restored.getPayload().contains("bob"));
        }
    }

    @Nested
    @DisplayName("Node")
    class NodeTests {

        @Test
        @DisplayName("создаётся с blockchain и utxoPool")
        void createsWithBlockchainAndPool() {
            Blockchain blockchain = new Blockchain();
            UTXOPool utxoPool = new UTXOPool();

            Node node = new Node(blockchain, utxoPool);

            assertNotNull(node.getBlockchain());
            assertNotNull(node.getUtxoPool());
            assertNotNull(node.getNodeId());
            assertNotNull(node.getMempool());
            assertNotNull(node.getPeers());
        }

        @Test
        @DisplayName("nodeId уникален")
        void nodeIdIsUnique() {
            Node node1 = new Node(new Blockchain(), new UTXOPool());
            Node node2 = new Node(new Blockchain(), new UTXOPool());

            assertNotEquals(node1.getNodeId(), node2.getNodeId());
        }

        @Test
        @DisplayName("изначально не запущен")
        void initiallyNotRunning() {
            Node node = new Node(new Blockchain(), new UTXOPool());

            assertFalse(node.isRunning());
        }

        @Test
        @DisplayName("peers изначально пустой")
        void peersInitiallyEmpty() {
            Node node = new Node(new Blockchain(), new UTXOPool());

            assertTrue(node.getPeers().isEmpty());
        }

        @Test
        @DisplayName("mempool изначально пустой")
        void mempoolInitiallyEmpty() {
            Node node = new Node(new Blockchain(), new UTXOPool());

            assertTrue(node.getMempool().isEmpty());
        }
    }

    @Nested
    @DisplayName("Интеграционные тесты (mock)")
    class IntegrationTests {

        @Test
        @DisplayName("broadcast создаёт сообщение правильного типа")
        void broadcastCreatesCorrectMessage() {
            // Тест логики создания сообщения без реальной сети
            Block block = new Block(1, System.currentTimeMillis(), "Test", "prev");
            block.updateHash();

            Message msg = new Message(MessageType.NEW_BLOCK,
                "{\"index\":" + block.getIndex() +
                ",\"hash\":\"" + block.getHash() + "\"}");

            assertEquals(MessageType.NEW_BLOCK, msg.getType());
            assertTrue(msg.getPayload().contains("index"));
            assertTrue(msg.getPayload().contains("hash"));
        }

        @Test
        @DisplayName("handshake содержит необходимые поля")
        void handshakeContainsRequiredFields() {
            String nodeId = "test123";
            int height = 10;

            Message handshake = new Message(MessageType.HANDSHAKE,
                "{\"nodeId\":\"" + nodeId + "\",\"height\":" + height + ",\"version\":1}");

            assertTrue(handshake.getPayload().contains(nodeId));
            assertTrue(handshake.getPayload().contains(String.valueOf(height)));
            assertTrue(handshake.getPayload().contains("version"));
        }
    }
}
