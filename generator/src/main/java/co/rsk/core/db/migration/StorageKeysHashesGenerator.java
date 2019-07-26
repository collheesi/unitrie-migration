/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.core.db.migration;

import co.rsk.RskContext;
import co.rsk.core.RskAddress;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.BlockStore;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StorageKeysHashesGenerator {

    private static final byte[] LAST_EXTRACTED_BLOCK_NUMBER = {0, 0, 0, 1};

    public static void main(String[] args) throws IOException {
        String databaseDir = "/database";
        BlockStore blockStore = RskContext.buildBlockStore(new BlockFactory(orchid()), databaseDir);
        KeyValueDataSource stateRootsTranslator = RskContext.makeDataSource("stateRoots", databaseDir);
        KeyValueDataSource unitrieDataSource = RskContext.makeDataSource("unitrie", databaseDir);
        TrieStoreImpl unitrieStore = new TrieStoreImpl(unitrieDataSource);

        Path destinationPath = Paths.get("/tmp", "migration-extras");
        Path outputPath = Paths.get("/output", "migration-extras");
        if (Files.exists(outputPath)) {
            Files.copy(outputPath, destinationPath);
        }

        try(DB indexDB = DBMaker.fileDB(destinationPath.toFile()).closeOnJvmShutdown().make()) {
            Map<byte[], byte[]> keccakPreimages = indexDB.hashMapCreate("preimages")
                    .keySerializer(Serializer.BYTE_ARRAY)
                    .valueSerializer(Serializer.BYTE_ARRAY)
                    .makeOrGet();
            Block bestBlock = blockStore.getBestBlock();
            long from = bytesToLong(keccakPreimages.getOrDefault(LAST_EXTRACTED_BLOCK_NUMBER, longToBytes(bestBlock.getNumber()))) + 1;
            long to = bestBlock.getNumber();

            System.out.printf("Collecting keys from block %d to %d", from, to);
            for(long i = from; i <= to; i++) {
                System.out.printf("\nCollecting keys for block %d\n", i);
                Block blockToExtractStorageKeys = blockStore.getChainBlockByNumber(i);
                Trie unitrieToExtractStorageKeys = unitrieStore.retrieve(stateRootsTranslator.get(blockToExtractStorageKeys.getStateRoot()));
                extractStorageKeysFromUnitrie(keccakPreimages, unitrieToExtractStorageKeys);
            }
            keccakPreimages.put(LAST_EXTRACTED_BLOCK_NUMBER, longToBytes(to));
            indexDB.commit();
            System.out.printf("\nTotal number of keys %d up to block %d\n", keccakPreimages.size(), to);
        }
        Files.copy(destinationPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void extractStorageKeysFromUnitrie(Map<byte[], byte[]> keccakPreimages, Trie unitrieToExtractStorageKeys) {
        Iterator<Trie.IterationElement> iterator = unitrieToExtractStorageKeys.getInOrderIterator();

        int collectedKeysCounter = 0;
        while (iterator.hasNext()) {
            Trie.IterationElement currentElement = iterator.next();
            int storageKeyUnitrieLength = (1 + TrieKeyMapper.SECURE_KEY_SIZE + RskAddress.LENGTH_IN_BYTES + 1 + TrieKeyMapper.SECURE_KEY_SIZE);
            TrieKeySlice nodeKey = currentElement.getNodeKey();
            if (nodeKey.length() > storageKeyUnitrieLength * Byte.SIZE && currentElement.getNode().isTerminal()) {
                byte[] encodedUnitrieKey = nodeKey.encode();
                byte[] storageKey = Arrays.copyOfRange(encodedUnitrieKey, storageKeyUnitrieLength, encodedUnitrieKey.length);
                byte[] storageKeyHash = Keccak256Helper.keccak256(DataWord.valueOf(storageKey).getData());
                keccakPreimages.put(storageKeyHash, storageKey);
                collectedKeysCounter++;
                if (collectedKeysCounter % 1000 == 0) {
                    System.out.print(".");
                }
                if (collectedKeysCounter % 72000 == 0) {
                    System.out.println();
                }
            }
        }
    }

    private static long bytesToLong(byte[] bytes) {
        return Long.parseLong(new String(bytes));
    }

    private static byte[] longToBytes(long number) {
        return String.valueOf(number).getBytes();
    }

    public static ActivationConfig orchid() {
        return only(
                ConsensusRule.RSKIP85,
                ConsensusRule.RSKIP87,
                ConsensusRule.RSKIP88,
                ConsensusRule.RSKIP89,
                ConsensusRule.RSKIP90,
                ConsensusRule.RSKIP91,
                ConsensusRule.RSKIP92,
                ConsensusRule.RSKIP94,
                ConsensusRule.RSKIP97,
                ConsensusRule.RSKIP98
        );
    }

    public static ActivationConfig only(ConsensusRule... upgradesToEnable) {
        Map<ConsensusRule, Long> consensusRules = EnumSet.allOf(ConsensusRule.class).stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> -1L));
        for (ConsensusRule consensusRule : upgradesToEnable) {
            consensusRules.put(consensusRule, 0L);
        }

        return new ActivationConfig(consensusRules);
    }

}

