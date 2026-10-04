// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.List;
import java.util.Map;

import org.apache.cassandra.stress.util.ResultLogger;

public class SettingsTokenRange
{
    public final boolean wrap;
    public final int splitFactor;
    private final TokenRangeOptions options;

    private SettingsTokenRange(TokenRangeOptions options)
    {
        this.options = options;
        this.wrap = options.wrap.setByUser();
        this.splitFactor = Math.toIntExact(OptionDistribution.parseLong(options.splitFactor.value()));
    }

    private static final class TokenRangeOptions extends GroupedOptions
    {
        final OptionSimple wrap = new OptionSimple("wrap", "", null, "Re-use token ranges in order to terminate stress iterations", false);
        final OptionSimple splitFactor = new OptionSimple("split-factor=", "[0-9]+[bmk]?", "1", "Split every token range by this factor", false);

        @Override
        public List<? extends Option> options()
        {
            return List.of(wrap, splitFactor);
        }
    }

    public static SettingsTokenRange get(Map<String, String[]> clArgs)
    {
        String[] params = clArgs.remove("-tokenrange");
        if (params == null)
        {
            return new SettingsTokenRange(new TokenRangeOptions());
        }
        TokenRangeOptions options = GroupedOptions.select(params, new TokenRangeOptions());
        if (options == null)
        {
            printHelp();
            System.out.println("Invalid -tokenrange options provided, see output for valid options");
            System.exit(1);
        }
        return new SettingsTokenRange(options);
    }

    public void printSettings(ResultLogger out)
    {
        out.printf("  Wrap: %b%n", wrap);
        out.printf("  Split Factor: %d%n", splitFactor);
    }

    public static void printHelp()
    {
        GroupedOptions.printOptions(System.out, "-tokenrange", new TokenRangeOptions());
    }

    public static Runnable helpPrinter()
    {
        return SettingsTokenRange::printHelp;
    }
}
